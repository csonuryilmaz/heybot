package operation;

import com.taskadapter.redmineapi.RedmineException;
import com.taskadapter.redmineapi.RedmineManager;
import com.taskadapter.redmineapi.RedmineManagerFactory;
import com.taskadapter.redmineapi.bean.Issue;
import java.util.Scanner;
import model.Command;
import utilities.Properties;
import static org.apache.http.util.TextUtils.isEmpty;

/**
 * Operaiton: Review.
 *
 * @author onuryilmaz
 */
public class Review extends Operation
{

    private RedmineManager redmineManager;

    //<editor-fold defaultstate="collapsed" desc="parameters">
    // mandatory
    private final static String PARAMETER_ISSUE = "ISSUE";
    private final static String PARAMETER_ISSUE_STATUS_TO_UPDATE = "ISSUE_STATUS_TO_UPDATE";
    private final static String PARAMETER_SUBVERSION_PATH = "SUBVERSION_PATH";
    private final static String PARAMETER_REDMINE_TOKEN = "REDMINE_TOKEN";
    private final static String PARAMETER_REDMINE_URL = "REDMINE_URL";
    // optional
    private final static String PARAMETER_ISSUE_STATUS_SHOULD_BE = "ISSUE_STATUS_SHOULD_BE";
    private final static String PARAMETER_SOURCE_PATH = "SOURCE_PATH";
    private final static String PARAMETER_IDE_PATH = "IDE_PATH";

//</editor-fold>
    public Review()
    {
	super(new String[]
	{
	    PARAMETER_ISSUE, PARAMETER_ISSUE_STATUS_TO_UPDATE, PARAMETER_SUBVERSION_PATH, PARAMETER_REDMINE_TOKEN, PARAMETER_REDMINE_URL
	}
	);
    }

    @Override
    public void execute(Properties prop)
    {
	if (areMandatoryParametersNotEmpty(prop))
	{
	    String issueId = getParameterString(prop, PARAMETER_ISSUE, false);
	    String targetStatus = getParameterString(prop, PARAMETER_ISSUE_STATUS_TO_UPDATE, false);
	    String sourceStatus = getParameterString(prop, PARAMETER_ISSUE_STATUS_SHOULD_BE, false);
	    String localWorkingDir = getParameterString(prop, PARAMETER_SOURCE_PATH, false);
	    String svnBranchDir = getParameterString(prop, PARAMETER_SUBVERSION_PATH, false);
	    String redmineAccessToken = getParameterString(prop, PARAMETER_REDMINE_TOKEN, false);
	    String redmineUrl = getParameterString(prop, PARAMETER_REDMINE_URL, false);

	    if (localWorkingDir == null || localWorkingDir.length() == 0)
	    {
		System.out.println("* Auto detecting source path ... ");
		localWorkingDir = tryExecute("pwd");
	    }
	    else if (localWorkingDir.endsWith("/"))
	    {
		localWorkingDir = localWorkingDir.substring(0, localWorkingDir.length() - 1);
	    }
	    System.out.println("[✓] SOURCE_PATH= " + localWorkingDir);

	    // connect redmine
	    redmineManager = RedmineManagerFactory.createWithApiKey(redmineUrl, redmineAccessToken);

	    // get issue
	    Issue issue = tryGetIssue(issueId);
	    if (issue != null)
	    {
		System.out.println("[✓] " + "#" + issue.getId() + " - " + issue.getSubject());
		if (isIssueStatusEligible(issue, sourceStatus, targetStatus))
		{
		    if (merge(localWorkingDir, svnBranchDir, issueId))
		    {
			setIssueStatus(issue, targetStatus);
			openIDE(prop, localWorkingDir);
		    }
		}
		else
		{
		    System.err.println("Ooops! Current issue status *" + issue.getStatusName() + "* is not eligible (*" + sourceStatus + "*).");
		}
	    }
	}
    }

    private Issue tryGetIssue(String issueId)
    {
	try
	{
	    return redmineManager.getIssueManager().getIssueById(Integer.parseInt(issueId));

	}
	catch (RedmineException ex)
	{
	    System.err.println(System.getProperty("line.separator") + "Ooops! Error while checking issue status " + issueId + ". (" + ex.getMessage() + ")");
	}
	catch (NumberFormatException ex)
	{
	    System.err.println(System.getProperty("line.separator") + "Ooops! Parse error for issue ISSUE. (" + ex.getMessage() + ")");
	}

	return null;
    }

    private boolean isIssueStatusEligible(Issue issue, String statusShouldBe, String statusWillBe)
    {
	if (statusShouldBe == null || statusShouldBe.length() == 0)
	{
	    return true;
	}

	String currentStatus = issue.getStatusName().toLowerCase(trLocale);

	if (currentStatus.equals(statusShouldBe.toLowerCase(trLocale)))
	{
	    return true;
	}

	return currentStatus.equals(statusWillBe.toLowerCase(trLocale));
    }

    private boolean merge(String localWorkingDir, String svnBranchDir, String issueId)
    {
	String svnCommand = tryExecute("which svn");

	if (svnCommand.length() == 0)// check svn command
	{
	    System.err.println("Ooops! Couldn't find SVN command.");
	    System.exit(0);
	}

	System.out.println();

	return svnStatus(svnCommand, localWorkingDir) && svnRevert(svnCommand, localWorkingDir) && svnUpdate(svnCommand, localWorkingDir) && svnShowEligibleRevisions(svnCommand, localWorkingDir, svnBranchDir, issueId) && svnMerge(svnCommand, localWorkingDir, svnBranchDir, issueId) && svnStatus(svnCommand, localWorkingDir);
    }

    private boolean svnMerge(String svnCommand, String localWorkingDir, String svnBranchDir, String issueId)
    {
	System.out.println("[*] Merging changes from branch to local working copy.");
	return executeSvnCommand(new Command(svnCommand + " merge --accept postpone " + getMergeSourceDir(svnCommand, localWorkingDir, svnBranchDir, issueId) + " " + localWorkingDir), localWorkingDir);
    }

    private boolean svnShowEligibleRevisions(String svnCommand, String localWorkingDir, String svnBranchDir, String issueId)
    {
	System.out.println("[*] List of eligible revisions that will be merged from branch.");
	return executeSvnCommand(new Command(svnCommand + " mergeinfo --show-revs eligible " + getMergeSourceDir(svnCommand, localWorkingDir, svnBranchDir, issueId)), localWorkingDir);
    }

    private boolean svnUpdate(String svnCommand, String localWorkingDir)
    {
	System.out.println("[*] Updating to latest.");
	return executeSvnCommand(new Command(svnCommand + " up " + localWorkingDir), localWorkingDir);
    }

    private boolean svnRevert(String svnCommand, String localWorkingDir)
    {
	Command svnStatusCmd = new Command(svnCommand + " st " + localWorkingDir);
	if (svnStatusCmd.execute() && !isEmpty(svnStatusCmd.toString()))
	{
	    Scanner scanner = new Scanner(System.in);
	    System.out.print("[?] There are some local changes. (Y)es to revert local changes or (N)o to continue merging. (Y/N)? ");
	    String answer = scanner.next();
	    if (!isEmpty(answer) && (answer.charAt(0) == 'Y' || answer.charAt(0) == 'y'))
	    {
		System.out.println("[*] Reverting local changes ...");
		return executeSvnCommand(new Command(svnCommand + " revert " + localWorkingDir + " --depth=infinity"), localWorkingDir);
	    }
	}
	return true;
    }

    private boolean svnStatus(String svnCommand, String localWorkingDir)
    {
	System.out.println("[*] Status in local working copy ...");
	return executeSvnCommand(new Command(svnCommand + " st " + localWorkingDir), localWorkingDir);
    }

    private boolean executeSvnCommand(Command command, String localWorkingDir)
    {
	if (command.execute())
	{
	    System.out.println(command.toString().replace(localWorkingDir + "/", "").replace(localWorkingDir, "."));
	    System.out.println("[✓]");
	    return true;
	}
	return false;
    }

    private void setIssueStatus(Issue issue, String status)
    {
	try
	{
	    int statusId = tryGetIssueStatusId(redmineManager, status);
	    if (statusId > 0)
	    {
		if (issue.getStatusId() != statusId)
		{
		    System.out.println("\n" + "- Trying to update #" + issue.getId() + " status to [" + status + "] ... ");

		    issue.setStatusId(statusId);
		    redmineManager.getIssueManager().update(issue);
		}
		checkIsIssueStatusIsUpdated(issue.getId(), statusId);
	    }
	}
	catch (RedmineException ex)
	{
	    System.err.println("Ooops! Could not update issue status to " + status + "!" + "(" + ex.getMessage() + ")");
	}
    }

    private String getMergeSourceDir(String svnCommand, String localWorkingDir, String svnBranchDir, String issueId)
    {
	if (svnBranchDir.charAt(svnBranchDir.length() - 1) != '/')
	{
	    svnBranchDir += "/";
	}

	String repoRoot = tryGetRepoRootDir(svnCommand, localWorkingDir);

	return svnBranchDir + "i" + issueId + "/" + getProjectName(repoRoot);
    }

    private void checkIsIssueStatusIsUpdated(int issueId, int statusId) throws RedmineException
    {
	Issue issue = redmineManager.getIssueManager().getIssueById(issueId);
	System.out.println("- Current status of #" + issueId + " is [" + issue.getStatusName() + "].");
	if (issue.getStatusId() != statusId)
	{
	    System.out.println("- [warning] It seems issue status couldn't be updated. Please check your redmine workflow or configuration!");
	}
    }

    private void openIDE(Properties prop, String localWorkingDir)
    {
	String idePath = getParameterString(prop, PARAMETER_IDE_PATH, false);
	if (!isEmpty(idePath))
	{
	    Scanner scanner = new Scanner(System.in);
	    System.out.print("[?] If merge has no conflicts, open IDE to review code changes. (Y/N)? ");
	    String answer = scanner.next();
	    if (!isEmpty(answer) && (answer.charAt(0) == 'Y' || answer.charAt(0) == 'y'))
	    {
		Command cmd = new Command(new String[]
		{
		    idePath, localWorkingDir
		});
		System.out.println("* Opening IDE ...");
		System.out.println(cmd.getCommandString());
		cmd.executeNoWait();
	    }
	}
    }

}

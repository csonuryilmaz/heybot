package operation;

import com.taskadapter.redmineapi.RedmineException;
import com.taskadapter.redmineapi.RedmineManager;
import com.taskadapter.redmineapi.RedmineManagerFactory;
import com.taskadapter.redmineapi.bean.Issue;
import java.io.File;
import java.util.Scanner;
import model.Command;
import static org.apache.http.util.TextUtils.isEmpty;
import utilities.Properties;

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
    private final static String PARAMETER_REPOSITORY_PATH = "REPOSITORY_PATH";
    private final static String PARAMETER_REDMINE_TOKEN = "REDMINE_TOKEN";
    private final static String PARAMETER_REDMINE_URL = "REDMINE_URL";
    private final static String PARAMETER_TRUNK_PATH = "TRUNK_PATH";
    private final static String PARAMETER_BRANCHES_PATH = "BRANCHES_PATH";
    private final static String PARAMETER_WORKSPACE_PATH = "WORKSPACE_PATH";
    // optional
    private final static String PARAMETER_ISSUE_STATUS_SHOULD_BE = "ISSUE_STATUS_SHOULD_BE";
    private final static String PARAMETER_IDE_PATH = "IDE_PATH";

//</editor-fold>
    public Review()
    {
	super(new String[]
	{
	    PARAMETER_ISSUE, PARAMETER_ISSUE_STATUS_TO_UPDATE, PARAMETER_REPOSITORY_PATH, PARAMETER_REDMINE_TOKEN, PARAMETER_REDMINE_URL, PARAMETER_TRUNK_PATH, PARAMETER_BRANCHES_PATH, PARAMETER_WORKSPACE_PATH
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
	    String redmineAccessToken = getParameterString(prop, PARAMETER_REDMINE_TOKEN, false);
	    String redmineUrl = getParameterString(prop, PARAMETER_REDMINE_URL, false);

	    redmineManager = RedmineManagerFactory.createWithApiKey(redmineUrl, redmineAccessToken);

	    Issue issue = tryGetIssue(issueId);
	    if (issue != null)
	    {
		System.out.println("[✓] " + "#" + issue.getId() + " - " + issue.getSubject());
		if (isIssueStatusEligible(issue, sourceStatus, targetStatus))
		{
		    if (createLocalWorkingCopyOfTrunk(prop, issue))
		    {
			if (merge(prop, issue))
			{
			    setIssueStatus(issue, targetStatus);
			    openIDE(prop, issue);
			}
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
	if (isEmpty(statusShouldBe))
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

    private boolean merge(Properties prop, Issue issue)
    {
	String svnCommand = tryExecute("which svn");
	String localWorkingCopy = trimRight(getParameterString(prop, PARAMETER_WORKSPACE_PATH, false), "/")
		+ "/" + "i" + issue.getId() + "r"
		+ "/" + new File(trimLeft(trimRight(getParameterString(prop, PARAMETER_TRUNK_PATH, false), "/"), "/")).getName();
	String branchPath = trimRight(getParameterString(prop, PARAMETER_REPOSITORY_PATH, false), "/")
		+ "/" + trimLeft(trimRight(getParameterString(prop, PARAMETER_BRANCHES_PATH, false), "/"), "/");
	String issueId = getParameterString(prop, PARAMETER_ISSUE, false);

	return svnStatus(svnCommand, localWorkingCopy)
		&& svnRevert(svnCommand, localWorkingCopy)
		&& svnUpdate(svnCommand, localWorkingCopy)
		&& svnShowEligibleRevisions(svnCommand, localWorkingCopy, branchPath, issueId)
		&& svnMerge(svnCommand, localWorkingCopy, branchPath, issueId)
		&& svnStatus(svnCommand, localWorkingCopy);
    }

    private boolean svnMerge(String svnCommand, String localWorkingDir, String svnBranchDir, String issueId)
    {
	System.out.println("[*] Merging changes from branch to local working copy.");
	return executeSvnCommand(new Command(svnCommand + " merge --accept postpone " + getMergeSourceDir(svnCommand, localWorkingDir, svnBranchDir, issueId) + " " + localWorkingDir), localWorkingDir);
    }

    private boolean svnShowEligibleRevisions(String svnCommand, String localWorkingDir, String svnBranchDir, String issueId)
    {
	System.out.println("[*] List of eligible revisions that will be merged from branch.");
	return executeSvnCommand(new Command(svnCommand + " mergeinfo --show-revs eligible " + getMergeSourceDir(svnCommand, localWorkingDir, svnBranchDir, issueId) + " " + localWorkingDir), localWorkingDir);
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

    private void openIDE(Properties prop, Issue issue)
    {
	String idePath = getParameterString(prop, PARAMETER_IDE_PATH, false);
	if (!isEmpty(idePath))
	{
	    String workspacePath = trimRight(getParameterString(prop, PARAMETER_WORKSPACE_PATH, false), "/");
	    Scanner scanner = new Scanner(System.in);
	    System.out.print("[?] Would you like open to IDE for development? (Y/N) ");
	    String answer = scanner.next();
	    if (!isEmpty(answer) && (answer.charAt(0) == 'Y' || answer.charAt(0) == 'y'))
	    {
		File projectPath = new File(workspacePath + "/" + "i" + issue.getId() + "r"
			+ "/" + new File(trimLeft(trimRight(getParameterString(prop, PARAMETER_TRUNK_PATH, false), "/"), "/")).getName());
		if (projectPath.exists())
		{
		    Command cmd = new Command(idePath.contains("%s") ? String.format(idePath, projectPath.getAbsolutePath())
			    : idePath + " " + projectPath.getAbsolutePath());
		    System.out.println("[*] Opening IDE ...");
		    System.out.println(cmd.getCommandString());
		    cmd.executeNoWait();
		}
		else
		{
		    System.out.print("Ooops! " + "Project not found in path:" + projectPath.getAbsolutePath());
		}
	    }
	}
    }

    private boolean createLocalWorkingCopyOfTrunk(Properties prop, Issue issue)
    {
	String workspacePath = trimRight(getParameterString(prop, PARAMETER_WORKSPACE_PATH, false), "/");
	String branchPath = workspacePath + "/" + "i" + issue.getId() + "r";
	if (deleteIfUserConfirmed(branchPath))
	{
	    String cachePath = getWorkingDirectory() + "/" + "cache";
	    createFolder(cachePath);

	    String repositoryPath = trimRight(getParameterString(prop, PARAMETER_REPOSITORY_PATH, false), "/");
	    cachePath += "/" + new File(repositoryPath).getName();
	    createFolder(cachePath);

	    String trunk = "/" + trimLeft(trimRight(getParameterString(prop, PARAMETER_TRUNK_PATH, false), "/"), "/");
	    File cache = new File(cachePath + trunk);
	    if (!cache.exists())
	    {
		svnCheckout(tryExecute("which svn"), repositoryPath + trunk, cache.getAbsolutePath());
	    }
	    if (svnUpdate(tryExecute("which svn"), cache.getAbsolutePath()))
	    {
		createFolder(branchPath);
		branchPath += "/" + cache.getName();
		if (copy(cache.getAbsolutePath(), branchPath))
		{
		    System.out.println("[✓] Local working copy is ready. \\o/");
		    return true;
		}
		else
		{
		    System.out.println("[e] Local working copy could not be created!");
		    return false;
		}
	    }
	}
	return true;
    }

    private boolean svnCheckout(String svnCommand, String source, String target)
    {
	String[] command = new String[]
	{
	    svnCommand, "checkout", "--quiet", source, target
	};
	System.out.println(svnCommand + " checkout " + source + " " + target);
	Thread thread = startFolderSizeProgress(target);
	String[] output = execute(command);
	stopFolderSizeProgress(thread, target);
	if (output == null || output[1].length() > 0)
	{
	    System.err.println(output[1]);
	    return false;
	}

	System.out.println(output[0]);
	return true;
    }

    private boolean deleteIfUserConfirmed(String localPath)
    {
	File file = new File(localPath);
	if (file.exists())
	{
	    System.out.println("[info] '" + file.getName() + "' is found in local workspace.");
	    Scanner scanner = new Scanner(System.in);
	    System.out.println("[?] Would you like to delete it to refresh local working copy? ");
	    System.out.print("[?] Choosing 'yes' will remove all unsaved local changes! (y/n)? ");
	    String answer = scanner.next();
	    if (!isEmpty(answer) && (answer.charAt(0) == 'Y' || answer.charAt(0) == 'y'))
	    {
		execute(new String[]
		{
		    "rm", "-Rf", localPath
		});
		System.out.println("[✓] Local working copy is deleted.");
		return true;
	    }
	    else
	    {
		System.out.println("[✓] Ok, continue using existing local working copy.");
		return false;
	    }
	}
	return true;
    }

    private boolean copy(String sourcePath, String targetPath)
    {
	String command = "cp -r " + sourcePath + " " + targetPath;
	System.out.println(command);
	String[] output = execute(command);
	if (output == null || output[1].length() > 0)
	{
	    System.err.println(output[1]);
	    return false;
	}

	System.out.println(output[0]);
	return true;
    }

}

package operation;

import com.taskadapter.redmineapi.RedmineException;
import com.taskadapter.redmineapi.RedmineManager;
import com.taskadapter.redmineapi.RedmineManagerFactory;
import com.taskadapter.redmineapi.bean.Issue;
import java.util.Locale;
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
    private final static String PARAMETER_SUBVERSION_PATH = "SUBVERSION_PATH";
    private final static String PARAMETER_REDMINE_TOKEN = "REDMINE_TOKEN";
    private final static String PARAMETER_REDMINE_URL = "REDMINE_URL";
    // optional
    private final static String PARAMETER_ISSUE_STATUS_SHOULD_BE = "ISSUE_STATUS_SHOULD_BE";
    private final static String PARAMETER_SOURCE_PATH = "SOURCE_PATH";

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
	    {// try to assign default argument
		localWorkingDir = tryExecute("pwd");
		System.out.println("(auto-detect) SOURCE_PATH= " + localWorkingDir);
	    }

	    // connect redmine
	    redmineManager = RedmineManagerFactory.createWithApiKey(redmineUrl, redmineAccessToken);

	    // get issue
	    Issue issue = tryGetIssue(issueId);
	    if (issue != null)
	    {
		if (isIssueStatusEligible(issue, sourceStatus, targetStatus))
		{
		    merge(localWorkingDir, svnBranchDir, issueId);
		    setIssueStatus(issue, targetStatus);
		}
		else
		{
		    System.err.println("Ooops! Current issue status is not eligible.");
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

	Locale trLocale = new Locale("tr-TR");

	String currentStatus = issue.getStatusName().toLowerCase(trLocale);

	if (currentStatus.equals(statusShouldBe.toLowerCase(trLocale)))
	{
	    return true;
	}

	return currentStatus.equals(statusWillBe.toLowerCase(trLocale));
    }

    private void merge(String localWorkingDir, String svnBranchDir, String issueId)
    {
	String svnCommand = tryExecute("which svn");

	if (svnCommand.length() == 0)// check svn command
	{
	    System.err.println("Ooops! Couldn't find SVN command.");
	    System.exit(0);
	}

	System.out.println();
	System.out.println("[ Current Status in Local Working Copy ]");
	System.out.println(tryExecute(svnCommand + " st " + localWorkingDir));
	System.out.println();
	System.out.println("[ Reverting Changes in Local Working Copy ]");
	System.out.println(tryExecute(svnCommand + " revert " + localWorkingDir + " --depth=infinity"));
	System.out.println();
	System.out.println("[ Updating Local Working Copy to Latest ]");
	System.out.println(tryExecute(svnCommand + " up " + localWorkingDir));
	System.out.println();
	System.out.println("[ Merging Changes From Branch ]");
	System.out.println(tryExecute(svnCommand + " merge --accept postpone " + getMergeSourceDir(svnCommand, localWorkingDir, svnBranchDir, issueId) + " " + localWorkingDir));
	System.out.println();
	System.out.println("[ Latest Status in Local Working Copy ]");
	System.out.println(tryExecute(svnCommand + " st " + localWorkingDir));
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

}

package operation;

import com.taskadapter.redmineapi.RedmineException;
import com.taskadapter.redmineapi.RedmineManager;
import com.taskadapter.redmineapi.RedmineManagerFactory;
import com.taskadapter.redmineapi.bean.Issue;
import com.taskadapter.redmineapi.bean.IssueStatus;
import java.util.List;
import java.util.Locale;
import java.util.Properties;

/**
 * Operaiton: Review.
 *
 * @author onuryilmaz
 */
public class Review extends Operation
{

    private RedmineManager redmineManager;

    @Override
    public void execute(Properties prop)
    {
	// try get parameters
	String issueId = prop.getProperty("ISSUE");
	String targetStatus = prop.getProperty("ISSUE_STATUS_TO_UPDATE");
	String sourceStatus = prop.getProperty("ISSUE_STATUS_SHOULD_BE");
	String localWorkingDir = prop.getProperty("SOURCE_PATH");
	String svnBranchDir = prop.getProperty("SUBVERSION_PATH");
	String redmineAccessToken = prop.getProperty("REDMINE_TOKEN");
	String redmineUrl = prop.getProperty("REDMINE_URL");
	String mergeRoot = prop.getProperty("MERGE_ROOT");

	if (issueId != null && targetStatus != null && svnBranchDir != null && redmineAccessToken != null && redmineUrl != null)
	{
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
		    merge(localWorkingDir, svnBranchDir, issueId, mergeRoot);
		    setIssueStatus(issue, targetStatus);
		}
		else
		{
		    System.err.println("Ooops! Current issue status is not eligible.");
		}
	    }
	}
	else
	{
	    System.err.println("Ooops! Missing required parameters.(ISSUE,ISSUE_STATUS_TO_UPDATE,SUBVERSION_PATH,REDMINE_TOKEN,REDMINE_URL)");
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

    private void merge(String localWorkingDir, String svnBranchDir, String issueId, String mergeRoot)
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
	if (mergeRoot == null || mergeRoot.length() == 0)
	{
	    mergeRoot = tryGetRepoRootDir(svnCommand, localWorkingDir);
	}
	System.out.println(tryExecute(svnCommand + " merge --accept postpone " + getMergeSourceDir(svnBranchDir, issueId, mergeRoot) + " " + localWorkingDir));
	System.out.println();
	System.out.println("[ Latest Status in Local Working Copy ]");
	System.out.println(tryExecute(svnCommand + " st " + localWorkingDir));
    }

    private void setIssueStatus(Issue issue, String targetStatus)
    {
	Locale trLocale = new Locale("tr-TR");

	try
	{
	    List<IssueStatus> list = redmineManager.getIssueManager().getStatuses();
	    int targetStatusId = 0;
	    for (IssueStatus status : list)
	    {
		if (status.getName().toLowerCase(trLocale).equals(targetStatus.toLowerCase(trLocale)))
		{
		    targetStatusId = status.getId();
		    break;
		}
	    }

	    if (issue.getStatusId() != targetStatusId)
	    {
		issue.setStatusId(targetStatusId);
		redmineManager.getIssueManager().update(issue);
		System.out.println(System.getProperty("line.separator") + "[Success]: Issue status is updated to " + targetStatus + ".");
	    }
	    else
	    {
		System.out.println(System.getProperty("line.separator") + "[Info]: Issue status is already " + targetStatus + ".");
	    }
	}
	catch (RedmineException ex)
	{
	    System.err.println("Ooops! Could not update issue status to " + targetStatus + "!" + "(" + ex.getMessage() + ")");
	}
    }

    private String getMergeSourceDir(String svnBranchDir, String issueId, String root)
    {
	if (svnBranchDir.charAt(svnBranchDir.length() - 1) != '/')
	{
	    svnBranchDir += "/";
	}

	root = root.replace("^", "");
	if (root.charAt(0) != '/')
	{
	    root = "/" + root;
	}

	return svnBranchDir + "i" + issueId + root;
    }

}

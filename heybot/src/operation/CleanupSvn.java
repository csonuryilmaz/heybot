package operation;

import com.taskadapter.redmineapi.RedmineException;
import com.taskadapter.redmineapi.RedmineManager;
import com.taskadapter.redmineapi.RedmineManagerFactory;
import com.taskadapter.redmineapi.bean.Issue;
import java.io.File;
import java.io.FilenameFilter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Locale;
import java.util.Properties;

/**
 * Operation: cleanup-svn
 *
 * @author onuryilmaz
 */
public class CleanupSvn extends Operation
{

    private RedmineManager redmineManager;
    private final Locale trLocale = new Locale("tr-TR");

    @Override
    public void execute(Properties prop)
    {
	// try get parameters
	String svnBranchPath = prop.getProperty("BRANCH_PATH");
	String status = prop.getProperty("STATUS");
	String redmineAccessToken = prop.getProperty("REDMINE_TOKEN");
	String redmineUrl = prop.getProperty("REDMINE_URL");
	String limit = prop.getProperty("LIMIT");

	int max;
	if (limit == null || limit.length() == 0)
	{
	    max = Integer.MAX_VALUE;
	}
	else
	{
	    max = Integer.parseInt(limit);
	}

	HashSet<String> statuses = null;
	if (status != null && status.length() > 0)
	{
	    statuses = new HashSet<>(Arrays.asList(status.toLowerCase(trLocale).split(",")));
	}

	if (svnBranchPath != null && statuses != null && statuses.size() > 0 && redmineAccessToken != null && redmineUrl != null)
	{
	    start(svnBranchPath, redmineAccessToken, redmineUrl, max, statuses);
	}
	else
	{
	    System.err.println("Ooops! Missing required parameters.(BRANCH_PATH,STATUS,REDMINE_TOKEN,REDMINE_URL)");
	}
    }

    private void start(String svnBranchPath, String redmineAccessToken, String redmineUrl, int max, HashSet<String> statuses)
    {
	redmineManager = RedmineManagerFactory.createWithApiKey(redmineUrl, redmineAccessToken);

	String svnCommand = tryExecute("which svn");
	if (svnCommand.length() == 0)// check svn command
	{
	    System.err.println("Ooops! Couldn't find SVN command.");
	    System.exit(0);
	}

	SvnBranch[] svnBranches = getSvnBranches(svnCommand, svnBranchPath);
	for (SvnBranch svnBranch : svnBranches)
	{
	    String issueStatus = getIssueStatus(svnBranch.getIssueId());
	    if (statuses.contains(issueStatus))
	    {
		System.out.println("ISSUE: " + svnBranch.getIssueId() + " is *" + issueStatus + "*");

		deleteSvnBranch(svnCommand, svnBranch);

		if (--max <= 0)
		{
		    break; // limit is reached!
		}
	    }
	}
    }

    private SvnBranch[] getSvnBranches(String svnCommand, String branchPath)
    {
	ArrayList<SvnBranch> branches = new ArrayList<>();

	String cmdOutput = tryExecute(svnCommand + " list " + branchPath);
	if (cmdOutput != null && cmdOutput.length() > 0)
	{
	    if (!branchPath.endsWith("/"))
	    {
		branchPath += "/";
	    }

	    String[] rows = cmdOutput.split("\n");
	    for (String row : rows)
	    {
		SvnBranch branch;
		if ((branch = tryGetSvnBranch(branchPath, row)) != null)
		{
		    branches.add(branch);
		}
	    }
	}

	return branches.toArray(new SvnBranch[0]);
    }

    private SvnBranch tryGetSvnBranch(String branchPath, String folder)
    {
	String issueId = folder.replaceAll("[^0-9]", "");
	if (issueId.length() > 0)
	{
	    try
	    {
		if (folder.endsWith("/"))
		{
		    folder = folder.substring(0, folder.length() - 1);
		}

		return new SvnBranch(Integer.parseInt(issueId), branchPath + folder);
	    }
	    catch (NumberFormatException nfe)
	    {
		// ignore, null will be returned
	    }
	}

	return null;
    }

    private String getIssueStatus(int issueId)
    {
	try
	{
	    Issue issue = redmineManager.getIssueManager().getIssueById(issueId);

	    return issue.getStatusName().toLowerCase(trLocale);
	}
	catch (RedmineException ex)
	{
	    System.err.println(System.getProperty("line.separator") + "Ooops! Error while checking issue status " + issueId + ". (" + ex.getMessage() + ")");
	}

	return ""; // unknown!
    }

    private void deleteSvnBranch(String svnCommand, SvnBranch svnBranch)
    {
	System.out.print("Deleting svn branch: " + svnBranch.getPath() + "  ...");
	if (tryExecute(new String[]
	{
	    svnCommand, "delete", "-m", "#" + svnBranch.getIssueId() + " - Branch is deleted by _heybot_.", svnBranch.getPath()
	}))
	{
	    System.out.println(" [✓]");
	}
    }

    class SvnBranch
    {

	private final int issueId;
	private final String path;

	SvnBranch(int issueId, String path)
	{
	    this.issueId = issueId;
	    this.path = path;
	}

	int getIssueId()
	{
	    return issueId;
	}

	String getPath()
	{
	    return path;
	}
    }

}

package operation;

import com.taskadapter.redmineapi.RedmineException;
import com.taskadapter.redmineapi.RedmineManager;
import com.taskadapter.redmineapi.RedmineManagerFactory;
import com.taskadapter.redmineapi.bean.Issue;
import java.io.File;
import java.io.FilenameFilter;
import java.util.ArrayList;
import java.util.Locale;
import java.util.Properties;

/**
 * Operation: cleanup
 *
 * @author onuryilmaz
 */
public class Cleanup extends Operation
{

    private final static String[] ISSUE_PREFIXES = new String[]
    {
	"#", "i"
    };
    private final static String ISSUE_STATUS_CLOSED = "Closed";

    private RedmineManager redmineManager;

    @Override
    public void execute(Properties prop)
    {
	// try get parameters
	String localWorkingDirectory = prop.getProperty("LOCAL_PATH");
	String svnBranchDirectory = prop.getProperty("SUBVERSION_PATH");
	String redmineAccessToken = prop.getProperty("REDMINE_TOKEN");
	String redmineUrl = prop.getProperty("REDMINE_URL");
	String limit = prop.getProperty("LIMIT");

	String svnCommand = tryExecute("which svn");

	if (svnCommand.length() == 0)// check svn command
	{
	    System.err.println("Ooops! Couldn't find SVN command.");
	}
	else
	{
	    int max;
	    if (limit == null || limit.length() == 0)
	    {
		max = Integer.MAX_VALUE;
	    }
	    else
	    {
		max = Integer.parseInt(limit);
	    }

	    if (localWorkingDirectory != null && svnBranchDirectory != null && redmineAccessToken != null && redmineUrl != null)
	    {
		start(localWorkingDirectory, svnBranchDirectory, redmineAccessToken, redmineUrl, max, svnCommand);
	    }
	    else
	    {
		//System.err.println(heybot.ERROR_MISSING_PARAMETERS);
	    }
	}
    }

    private void start(String localWorkingDirectory, String svnBranchDirectory, String redmineAccessToken, String redmineUrl, int max, String svnCommand)
    {
	redmineManager = RedmineManagerFactory.createWithApiKey(redmineUrl, redmineAccessToken);

	String[] branches = getBranches(localWorkingDirectory);
	for (String branch : branches)
	{
	    int issueId = getIssueId(branch);
	    Boolean isIssueClosed = isIssueClosed(issueId);
	    if (isIssueClosed != null && isIssueClosed == true)
	    {
		System.out.println("ISSUE: " + issueId + " is closed.");

		deleteBranchFromSvnRepository(svnCommand, svnBranchDirectory, branch, issueId);
		deleteBranch(localWorkingDirectory, branch);

		if (--max <= 0)
		{
		    break; // limit is reached!
		}
	    }
	}
    }

    private String[] getBranches(String dir)
    {
	String[] directories = new File(dir).list(new FilenameFilter()
	{
	    @Override
	    public boolean accept(File current, String name)
	    {
		return new File(current, name).isDirectory();
	    }
	});

	ArrayList<String> branches = new ArrayList<>();

	if (directories != null)
	{
	    String branch;
	    for (String entry : directories)
	    {
		if ((branch = tryGetBranch(entry)) != null)
		{
		    branches.add(branch);
		}
	    }
	}
	return branches.toArray(new String[0]);
    }

    private String tryGetBranch(String text)
    {
	text = text.toLowerCase(new Locale("tr-TR"));
	if (isBranch(text))
	{
	    return text;
	}

	return null;
    }

    private boolean isBranch(String text)
    {
	for (String prefix : ISSUE_PREFIXES)
	{
	    if (text.startsWith(prefix) && isNumeric(text.substring(1)))
	    {
		return true;
	    }
	}

	return false;
    }

    private boolean isNumeric(String str)
    {
	try
	{
	    int i;

	    i = Integer.parseInt(str);
	}
	catch (NumberFormatException nfe)
	{
	    return false;
	}
	return true;
    }

    private int getIssueId(String branch)
    {
	return Integer.parseInt(branch.substring(1));
    }

    private Boolean isIssueClosed(int issueId)
    {
	try
	{
	    Issue issue = redmineManager.getIssueManager().getIssueById(issueId);

	    return issue.getStatusName().equals(ISSUE_STATUS_CLOSED);
	}
	catch (RedmineException ex)
	{
	    System.err.println(System.getProperty("line.separator") + "Ooops! Error while checking issue status " + issueId + ". (" + ex.getMessage() + ")");
	}

	return null; // unknown!
    }

    private void deleteBranchFromSvnRepository(String svnCommand, String svnBranchDirectory, String branch, int issueId)
    {
	String path;
	if (svnBranchDirectory.endsWith("/"))
	{
	    path = svnBranchDirectory + branch;
	}
	else
	{
	    path = svnBranchDirectory + "/" + branch;
	}

	System.out.println("Deleting from " + path + "  ...");
	if (tryExecute(new String[]
	{
	    svnCommand, "delete", path, "-m", "#" + issueId + " Branch is deleted by *heybot* cleanup."
	}))
	{
	    System.out.println("  [✓]");
	}
    }

    private void deleteBranch(String sftpBranchLocDir, String branch)
    {
	String path;
	if (sftpBranchLocDir.endsWith("/"))
	{
	    path = sftpBranchLocDir + branch;
	}
	else
	{
	    path = sftpBranchLocDir + "/" + branch;
	}

	System.out.println("Deleting from " + path + "  ...");

	if (tryExecute(new String[]
	{
	    "rm", "-Rf", path
	}))
	{
	    System.out.println("  [✓]");
	}
    }

}

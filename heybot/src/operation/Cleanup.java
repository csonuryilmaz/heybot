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
 * Operation: cleanup
 *
 * @author onuryilmaz
 */
public class Cleanup extends Operation
{

    private RedmineManager redmineManager;
    private final Locale trLocale = new Locale("tr-TR");

    //<editor-fold defaultstate="collapsed" desc="parameters">
    //mandatory
    private final static String PARAMETER_LOCAL_PATH = "LOCAL_PATH";
    private final static String PARAMETER_STATUS = "STATUS";
    private final static String PARAMETER_REDMINE_TOKEN = "REDMINE_TOKEN";
    private final static String PARAMETER_REDMINE_URL = "REDMINE_URL";
    // optional
    private final static String PARAMETER_LIMIT = "LIMIT";

//</editor-fold>
    public Cleanup()
    {
	super(new String[]
	{
	    PARAMETER_LOCAL_PATH, PARAMETER_STATUS, PARAMETER_REDMINE_TOKEN, PARAMETER_REDMINE_URL
	}
	);
    }

    @Override
    public void execute(Properties prop)
    {
	if (areMandatoryParametersNotEmpty(prop))
	{
	    // try get parameters
	    String localWorkingDirectory = getParameterString(prop, PARAMETER_LOCAL_PATH, false);
	    HashSet<String> statuses = getParameterStringHash(prop, PARAMETER_STATUS, true);
	    String redmineAccessToken = getParameterString(prop, PARAMETER_REDMINE_TOKEN, false);
	    String redmineUrl = getParameterString(prop, PARAMETER_REDMINE_URL, false);

	    int max = getParameterInt(prop, PARAMETER_LIMIT, Integer.MAX_VALUE);

	    start(localWorkingDirectory, redmineAccessToken, redmineUrl, max, statuses);
	}
    }

    private void start(String localWorkingDirectory, String redmineAccessToken, String redmineUrl, int max, HashSet<String> statuses)
    {
	redmineManager = RedmineManagerFactory.createWithApiKey(redmineUrl, redmineAccessToken);

	Branch[] branches = getBranches(localWorkingDirectory);
	for (Branch branch : branches)
	{
	    String issueStatus = getIssueStatus(branch.getIssueId());
	    if (statuses.contains(issueStatus))
	    {
		System.out.println("ISSUE: " + branch.getIssueId() + " is *" + issueStatus + "*");

		deleteBranch(localWorkingDirectory, branch);

		if (--max <= 0)
		{
		    break; // limit is reached!
		}
	    }
	}
    }

    private Branch[] getBranches(String dir)
    {
	String[] directories = new File(dir).list(new FilenameFilter()
	{
	    @Override
	    public boolean accept(File current, String name)
	    {
		return new File(current, name).isDirectory();
	    }
	});

	ArrayList<Branch> branches = new ArrayList<>();

	if (directories != null)
	{
	    Branch branch;
	    for (String directory : directories)
	    {
		if ((branch = tryGetBranch(directory)) != null)
		{
		    branches.add(branch);
		}
	    }
	}
	return branches.toArray(new Branch[0]);
    }

    private Branch tryGetBranch(String directory)
    {
	String issueId = directory.replaceAll("[^0-9]", "");
	if (issueId.length() > 0)
	{
	    try
	    {
		return new Branch(Integer.parseInt(issueId), directory);
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

    private void deleteBranch(String sftpBranchLocDir, Branch branch)
    {
	String path;
	if (sftpBranchLocDir.endsWith("/"))
	{
	    path = sftpBranchLocDir + branch.getDirectory();
	}
	else
	{
	    path = sftpBranchLocDir + "/" + branch.getDirectory();
	}

	System.out.print("Deleting from " + path + "  ...");
	if (tryExecute(new String[]
	{
	    "rm", "-Rf", path
	}))
	{
	    System.out.println(" [✓]");
	}
    }

    class Branch
    {

	private final int issueId;
	private final String directory;

	Branch(int issueId, String directory)
	{
	    this.issueId = issueId;
	    this.directory = directory;
	}

	int getIssueId()
	{
	    return issueId;
	}

	String getDirectory()
	{
	    return directory;
	}
    }

}

package operation;

import com.taskadapter.redmineapi.RedmineException;
import com.taskadapter.redmineapi.RedmineManager;
import com.taskadapter.redmineapi.RedmineManagerFactory;
import com.taskadapter.redmineapi.bean.Issue;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Locale;
import utilities.Properties;

/**
 * Operation: cleanup-svn
 *
 * @author onuryilmaz
 */
public class CleanupSvn extends Operation
{

    private RedmineManager redmineManager;
    private final Locale trLocale = new Locale("tr-TR");

    //<editor-fold defaultstate="collapsed" desc="parameters">
    // mandatory
    private final static String PARAMETER_BRANCH_PATH = "BRANCH_PATH";
    private final static String PARAMETER_STATUS = "STATUS";
    private final static String PARAMETER_REDMINE_TOKEN = "REDMINE_TOKEN";
    private final static String PARAMETER_REDMINE_URL = "REDMINE_URL";
    // optional
    private final static String PARAMETER_LIMIT = "LIMIT";

//</editor-fold>
    public CleanupSvn()
    {
	super(new String[]
	{
	    PARAMETER_BRANCH_PATH, PARAMETER_STATUS, PARAMETER_REDMINE_TOKEN, PARAMETER_REDMINE_URL
	}
	);
    }

    @Override
    public void execute(Properties prop)
    {
	if (areMandatoryParametersNotEmpty(prop))
	{
	    String svnBranchPath = getParameterString(prop, PARAMETER_BRANCH_PATH, false);
	    HashSet<String> statuses = getParameterStringHash(prop, PARAMETER_STATUS, true);
	    String redmineAccessToken = getParameterString(prop, PARAMETER_REDMINE_TOKEN, false);
	    String redmineUrl = getParameterString(prop, PARAMETER_REDMINE_URL, false);

	    int max = getParameterInt(prop, PARAMETER_LIMIT, Integer.MAX_VALUE);

	    start(svnBranchPath, redmineAccessToken, redmineUrl, max, statuses);
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

package operation;

import com.taskadapter.redmineapi.RedmineManager;
import com.taskadapter.redmineapi.RedmineManagerFactory;
import com.taskadapter.redmineapi.bean.Issue;
import java.util.Properties;

/**
 *
 * @author onuryilmaz
 */
public class NextVersion extends Operation
{
    //<editor-fold defaultstate="collapsed" desc="parameters">

    // mandatory
    private final static String PARAMETER_REDMINE_TOKEN = "REDMINE_TOKEN";
    private final static String PARAMETER_REDMINE_URL = "REDMINE_URL";
    private final static String PARAMETER_FILTER_PROJECT = "FILTER_PROJECT";
    private final static String PARAMETER_FILTER_QUERY = "FILTER_QUERY";
    // optional

    //</editor-fold>
    private RedmineManager redmineManager;

    public NextVersion()
    {
	super(new String[]
	{
	    PARAMETER_REDMINE_TOKEN, PARAMETER_REDMINE_URL, PARAMETER_FILTER_PROJECT, PARAMETER_FILTER_QUERY
	}
	);
    }

    @Override
    public void execute(Properties prop)
    {
	if (areMandatoryParametersNotEmpty(prop))
	{
	    // mandatory
	    String redmineAccessToken = getParameterString(prop, PARAMETER_REDMINE_TOKEN, false);
	    String redmineUrl = getParameterString(prop, PARAMETER_REDMINE_URL, false);
	    String filterProject = getParameterString(prop, PARAMETER_FILTER_PROJECT, true);
	    String filterQuery = getParameterString(prop, PARAMETER_FILTER_QUERY, true);
	    // optional

	    redmineManager = RedmineManagerFactory.createWithApiKey(redmineUrl, redmineAccessToken);

	    int filterSavedQueryId = tryGetSavedQueryId(redmineManager, filterProject, filterQuery);
	    if (filterSavedQueryId > 0)
	    {
		Issue[] issues = getProjectIssues(redmineManager, filterProject, filterSavedQueryId);
		if (issues.length > 0)
		{
		    for (Issue issue : issues)
		    {
			System.out.println("#" + issue.getId() + " " + issue.getSubject());
		    }
		}
		else
		{
		    System.out.println("There is no ready to release and unversioned issue fouund.");
		}

		System.out.println();
	    }
	    else
	    {
		System.err.println("Ooops! Couldn't find saved query. Saved query contains ready and unversioned issues.");
	    }

	    System.out.println("in progress ...");
	}
    }

}

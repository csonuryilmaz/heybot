package model;

import com.taskadapter.redmineapi.bean.Issue;
import java.util.HashSet;
import java.util.Locale;

/**
 *
 * @author onuryilmaz
 */
public class VersionTag
{

    private final Locale trLocale = new Locale("tr-TR");

    private final HashSet<String> majorTrackers;
    private final HashSet<String> minorTrackers;
    private final HashSet<String> patchTrackers;

    private int constant;
    private int major;
    private int minor;
    private int patch;

    public VersionTag(String versionTag, HashSet<String> majorTrackers, HashSet<String> minorTrackers, HashSet<String> patchTrackers) throws Exception
    {
	this.majorTrackers = majorTrackers;
	this.minorTrackers = minorTrackers;
	this.patchTrackers = patchTrackers;

	if (versionTag == null || versionTag.length() == 0)
	{
	    versionTag = "1.0.0.0";
	}

	parse(versionTag);
    }

    public void next(Issue[] issues)
    {
	int majorCnt = 0, minorCnt = 0, patchCnt = 0;

	for (Issue issue : issues)
	{
	    String tracker = getTracker(issue);
	    if (majorTrackers.contains(tracker))
	    {
		majorCnt++;
	    }
	    else if (minorTrackers.contains(tracker))
	    {
		minorCnt++;
	    }
	    else if (patchTrackers.contains(tracker))
	    {
		patchCnt++;
	    }
	}

	if (majorCnt > 0)
	{
	    major += majorCnt;
	    minor = 0;
	    patch = 0;
	}

	if (minorCnt > 0)
	{
	    minor += minorCnt;
	    patch = 0;
	}

	if (patchCnt > 0)
	{
	    patch += patchCnt;
	}
    }

    @Override
    public String toString()
    {
	return constant + "." + major + "." + minor + "." + patch;
    }

    private void parse(String versionTag) throws Exception
    {
	String[] tokens = versionTag.split("\\.");
	if (tokens.length != 4)
	{
	    throw new Exception("Version tag is unrecognized! Input: " + versionTag + " Expected: x.x.x.x (x is a numeric value)");
	}

	this.constant = Integer.parseInt(tokens[0]);
	this.major = Integer.parseInt(tokens[1]);
	this.minor = Integer.parseInt(tokens[2]);
	this.patch = Integer.parseInt(tokens[3]);
    }

    private String getTracker(Issue issue)
    {
	return issue.getTracker().getName().toLowerCase(trLocale);
    }

}

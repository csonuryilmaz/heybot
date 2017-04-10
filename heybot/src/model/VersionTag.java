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

    private Tag currentTag, previousTag;

    public VersionTag(String versionTag, HashSet<String> majorTrackers, HashSet<String> minorTrackers, HashSet<String> patchTrackers) throws Exception
    {
	this.majorTrackers = majorTrackers;
	this.minorTrackers = minorTrackers;
	this.patchTrackers = patchTrackers;

	if (versionTag == null || versionTag.length() == 0)
	{
	    versionTag = "1.0.0.0";
	}

	this.currentTag = parse(versionTag);
    }

    public VersionTag(String versionTag, String previousVersionTag, HashSet<String> majorTrackers, HashSet<String> minorTrackers, HashSet<String> patchTrackers) throws Exception
    {
	this.majorTrackers = majorTrackers;
	this.minorTrackers = minorTrackers;
	this.patchTrackers = patchTrackers;

	if (versionTag == null || versionTag.length() == 0)
	{
	    versionTag = "1.0.0.0";
	}

	this.currentTag = parse(versionTag);
	this.previousTag = parse(previousVersionTag);
    }

    public void next(Issue[] issues)
    {
	int majorCnt, minorCnt, patchCnt;

	if (previousTag != null)
	{// append operation
	    majorCnt = currentTag.major - previousTag.major;
	    if (majorCnt > 0)
	    {
		minorCnt = currentTag.minor;
		patchCnt = currentTag.patch;
	    }
	    else
	    {
		minorCnt = currentTag.minor - previousTag.minor;
		if (minorCnt > 0)
		{
		    patchCnt = currentTag.patch;
		}
		else
		{
		    patchCnt = currentTag.patch - previousTag.patch;
		}
	    }
	}
	else
	{// create operation
	    majorCnt = 0;
	    minorCnt = 0;
	    patchCnt = 0;
	}

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

	if (previousTag != null)
	{
	    currentTag = getNextTag(previousTag, majorCnt, minorCnt, patchCnt);
	}
	else
	{
	    currentTag = getNextTag(currentTag, majorCnt, minorCnt, patchCnt);
	}
    }

    @Override
    public String toString()
    {
	return currentTag.toString();
    }

    private Tag parse(String versionTag) throws Exception
    {
	String[] tokens = versionTag.split("\\.");
	if (tokens.length != 4)
	{
	    throw new Exception("Version tag is unrecognized! Input: " + versionTag + " Expected: x.x.x.x (x is a numeric value)");
	}

	return new Tag(Integer.parseInt(tokens[0]), Integer.parseInt(tokens[1]), Integer.parseInt(tokens[2]), Integer.parseInt(tokens[3]));
    }

    private String getTracker(Issue issue)
    {
	return issue.getTracker().getName().toLowerCase(trLocale);
    }

    private Tag getNextTag(Tag tag, int majorCnt, int minorCnt, int patchCnt)
    {
	int constant = tag.constant;
	int major = tag.major;
	int minor = tag.minor;
	int patch = tag.patch;

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

	return new Tag(constant, major, minor, patch);
    }

    private class Tag
    {

	private final int constant;
	private final int major;
	private final int minor;
	private final int patch;

	Tag(int constant, int major, int minor, int patch)
	{
	    this.constant = constant;
	    this.major = major;
	    this.minor = minor;
	    this.patch = patch;
	}

	@Override
	public String toString()
	{
	    return constant + "." + major + "." + minor + "." + patch;
	}

    }

}

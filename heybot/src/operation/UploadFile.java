package operation;

import com.jcraft.jsch.ChannelSftp;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.Session;
import com.jcraft.jsch.SftpATTRS;
import com.jcraft.jsch.SftpException;
import com.jcraft.jsch.SftpProgressMonitor;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import org.apache.commons.lang3.StringUtils;
import utilities.Properties;

public class UploadFile extends Operation
{

    // mandatory
    private final static String PARAMETER_REMOTE_HOST = "REMOTE_HOST";
    private final static String PARAMETER_REMOTE_USER = "REMOTE_USER";
    private final static String PARAMETER_REMOTE_PASS = "REMOTE_PASS";
    private final static String PARAMETER_REMOTE_WORKSPACE = "REMOTE_WORKSPACE";
    private final static String PARAMETER_SOURCE_WORKSPACE = "SOURCE_WORKSPACE";
    // optional
    private final static String PARAMETER_REMOTE_PORT = "REMOTE_PORT";

    private final File file;

    public UploadFile(String filePath) throws Exception
    {
	super(new String[]{
	    PARAMETER_REMOTE_HOST, PARAMETER_REMOTE_USER, PARAMETER_REMOTE_PASS, PARAMETER_REMOTE_WORKSPACE, PARAMETER_SOURCE_WORKSPACE
	});
	if (!StringUtils.isBlank(filePath)) {
	    file = new File(filePath);
	    if (!file.exists()) {
		throw new Exception("[e] File could not be found! Please check file whether exists or parameter value is correct."
			+ System.getProperty("line.separator") + "[e] File: " + file.getAbsolutePath());
	    }
	}
	else {
	    throw new Exception("[e] File argument could not be passed empty!");
	}
    }

    @Override
    protected void execute(Properties prop) throws Exception
    {
	if (areMandatoryParametersNotEmpty(prop)) {
	    String[] sftpHosts = getParameterStringArray(prop, PARAMETER_REMOTE_HOST, false);
	    String sftpUser = getParameterString(prop, PARAMETER_REMOTE_USER, false);
	    String sftpPass = getParameterString(prop, PARAMETER_REMOTE_PASS, false);
	    int sftpPort = getParameterInt(prop, PARAMETER_REMOTE_PORT, 22);
	    String remoteWorkspace = trimRight(getParameterString(prop, PARAMETER_REMOTE_WORKSPACE, false), "/");
	    String sourceWorkspace = trimRight(getParameterString(prop, PARAMETER_SOURCE_WORKSPACE, false), "/");

	    System.out.println("[i] Upload File: " + file.getAbsolutePath());
	    System.out.println("[i] SftpPort: " + sftpPort);
	    System.out.println("[i] SftpUser: " + sftpUser);
	    System.out.println("[i] SourceWS: " + sourceWorkspace);
	    System.out.println("[i] RemoteWS: " + remoteWorkspace);

	    if (file.getAbsolutePath().indexOf(sourceWorkspace) != 0) {
		throw new Exception("[e] Source workspace and file path mismatch! Uploaded file or directory must be under defined source workspace.");
	    }
	    File remoteFile = new File(file.getAbsolutePath().replace(sourceWorkspace, remoteWorkspace));
	    for (String sftpHost : sftpHosts) {
		System.out.println("==========================[ FTP ]==========================");
		System.out.println("[i] Host: " + sftpHost + getHostIPAddress(sftpHost));
		uploadFile(sftpHost, sftpUser, sftpPass, sftpPort, file, remoteFile);
	    }
	}
    }

    private String getHostIPAddress(String host)
    {
	try {
	    return " (" + InetAddress.getByName(host).getHostAddress() + ")";
	}
	catch (UnknownHostException ex) {
	    return "";
	}
    }

    private void uploadFile(String sftpHost, String sftpUser, String sftpPass, int sftpPort, File sourceFile, File remoteFile) throws JSchException, SftpException, FileNotFoundException
    {
	JSch jsch = new JSch();
	Session session = jsch.getSession(sftpUser, sftpHost, sftpPort);
	session.setPassword(sftpPass);
	java.util.Properties config = new java.util.Properties();
	config.put("StrictHostKeyChecking", "no");
	session.setConfig(config);
	System.out.println("[*] \tConnecting ...");
	session.connect();
	System.out.println("[i] \tConnected. \\o/");

	ChannelSftp channel = (ChannelSftp) session.openChannel("sftp");
	channel.connect();

	File remoteDir;
	int permissions;
	if (sourceFile.isFile()) {
	    remoteDir = remoteFile.getParentFile();
	    permissions = getPathPermissions(sourceFile.getParentFile());
	}
	else {
	    remoteDir = remoteFile;
	    permissions = getPathPermissions(sourceFile);
	}
	makeRemoteDirIfNotExists(channel, remoteDir, permissions);
	if (sourceFile.isFile()) {
	    channel.put(new FileInputStream(sourceFile), remoteFile.getAbsolutePath(), new UploadFile.UploadProgressMonitor(), ChannelSftp.OVERWRITE);
	}

	channel.disconnect();
	session.disconnect();
    }

    private void makeRemoteDirIfNotExists(ChannelSftp channel, File remoteDir, int permissions) throws SftpException
    {
	try {
	    SftpATTRS attr = channel.stat(remoteDir.getAbsolutePath());
	    System.out.println("[i] Remote directory exists: " + remoteDir.getAbsolutePath() + "\t" + attr.toString());
	}
	catch (SftpException ex) {
	    if (ex.id == 2) {
		System.out.println("[i] No such directory: " + remoteDir.getAbsolutePath()
			+ "\n\t" + "Creating ...");
		if (remoteDir.getParentFile() != null) {
		    makeRemoteDirIfNotExists(channel, remoteDir.getParentFile(), 0);
		}
		channel.mkdir(remoteDir.getAbsolutePath());
		if (permissions > 0) {
		    channel.chmod(permissions, remoteDir.getAbsolutePath());
		}
		System.out.println("[✓] " + remoteDir.getAbsolutePath());
	    }
	}
    }

    private int getPathPermissions(File file)
    {
	String[] result = execute(new String[]{"stat", "-c", "%a %n", file.getAbsolutePath()});
	return result[2].equals("0") ? Integer.parseInt(result[0].split(" ")[0], 8) : 0;
    }

    class UploadProgressMonitor implements SftpProgressMonitor
    {

	UploadProgressMonitor()
	{
	}

	@Override
	public void init(int op, java.lang.String src, java.lang.String dest, long max)
	{
	    System.out.println("[>] " + dest);
	}

	private long total;

	public boolean count(long bytes)
	{
	    total += bytes;
	    return true;
	}

	public void end()
	{
	    System.out.println("[✓] " + total + " bytes ");
	}
    }

}

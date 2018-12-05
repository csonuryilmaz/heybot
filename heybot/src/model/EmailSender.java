package model;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.util.Properties;
import javax.activation.DataHandler;
import javax.mail.Address;
import javax.mail.Message;
import javax.mail.MessagingException;
import javax.mail.Multipart;
import javax.mail.Session;
import javax.mail.Transport;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeBodyPart;
import javax.mail.internet.MimeMessage;
import javax.mail.internet.MimeMultipart;
import javax.mail.util.ByteArrayDataSource;
import org.apache.http.util.TextUtils;

public class EmailSender
{

    private final String username;
    private final String password;
    private final String host;
    private final String port;
    private final String starttlsEnable;
    private final String replyTo;

    public EmailSender(String username, String password, String host, String port, String starttlsEnable, String replyTo) throws Exception
    {
	if (!TextUtils.isEmpty(username) && !TextUtils.isEmpty(password) && !TextUtils.isEmpty(host) && !TextUtils.isEmpty(port) && !TextUtils.isEmpty(starttlsEnable))
	{
	    this.username = username;
	    this.password = password;
	    this.host = host;
	    this.port = port;
	    this.starttlsEnable = starttlsEnable;
	    this.replyTo = replyTo;
	}
	else
	{
	    throw new Exception("Missing parameters! Please check SMTP parameters.");
	}
    }

    public void send(String[] recipients, String subject, String body) throws Exception
    {
	try
	{
	    Properties props = new Properties();
	    props.put("mail.smtp.port", port);
	    props.put("mail.smtp.auth", "true");
	    props.put("mail.smtp.starttls.enable", starttlsEnable);

	    Session session = Session.getDefaultInstance(props);

	    Transport transport = session.getTransport("smtp");
	    transport.connect(host, username, password);

	    MimeMessage msg = new MimeMessage(session);
	    System.out.println("[i] List of recipients for e-mail notification:");
	    for (String recipient : recipients)
	    {
		System.out.println("[.] \t" + recipient);
		msg.addRecipient(Message.RecipientType.BCC, new InternetAddress(recipient, recipient, "ISO-8859-9"));
	    }
	    msg.setFrom(new InternetAddress(username, username, "ISO-8859-9"));
	    if (!TextUtils.isEmpty(host))
	    {
		msg.setReplyTo(new Address[]
		{
		    new InternetAddress(replyTo, replyTo, "ISO-8859-9")
		});
	    }
	    msg.setSubject(subject, "ISO-8859-9");
	    msg.setHeader("X-Mailer", "heybot-relase-notification");

	    MimeBodyPart mbp1 = new MimeBodyPart();
	    mbp1.setDataHandler(new DataHandler(new ByteArrayDataSource(body, "text/html; charset=\"UTF-8\"")));

	    Multipart mp = new MimeMultipart();
	    mp.addBodyPart(mbp1);
	    msg.setContent(mp);

	    System.out.println("[i] Subject: " + subject);
	    transport.sendMessage(msg, msg.getAllRecipients());
	}
	catch (MessagingException | IOException ex)
	{
	    throw new Exception("Smtp sending error!" + ex.getMessage());
	}
    }
}

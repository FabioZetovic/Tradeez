package com.tradeez.identity.mail;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedDeque;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * T56: mail that never leaves this machine. Each message is written to a file in {@code tradeez.mail.outbox-dir}
 * (gitignored) for a developer to open, and the last few are kept in memory for the tests. Refused in production by
 * {@code MailGuard}: there nobody would ever read them.
 *
 * <p>The log names the file and the subject only — never the address or the body, which holds a single-use link.
 */
@Component
@ConditionalOnProperty(name = "tradeez.mail.provider", havingValue = "outbox", matchIfMissing = true)
public class OutboxMailer implements Mailer {

	private static final Logger log = LoggerFactory.getLogger(OutboxMailer.class);

	private static final int KEPT = 50;

	private final Path dir;
	private final Deque<Mail> recent = new ConcurrentLinkedDeque<>();

	public OutboxMailer(@Value("${tradeez.mail.outbox-dir:}") String dir) {
		this.dir = dir.isBlank() ? null : Path.of(dir);
	}

	@Override
	public void send(Mail mail) {
		recent.addLast(mail);
		while (recent.size() > KEPT) {
			recent.pollFirst();
		}
		if (dir == null) {
			return;
		}
		try {
			Files.createDirectories(dir);
			Path file = dir.resolve(Instant.now().toEpochMilli() + "-" + recent.size() + ".txt");
			Files.writeString(file, "To: " + mail.to() + "\nSubject: " + mail.subject() + "\n\n" + mail.text(),
				StandardCharsets.UTF_8);
			log.info("Mail \"{}\" written to {}", mail.subject(), file.toAbsolutePath());
		} catch (IOException e) {
			log.warn("Could not write a mail to the outbox {}", dir, e);
		}
	}

	/** The messages sent most recently, oldest first. */
	public List<Mail> recent() {
		return new ArrayList<>(recent);
	}
}

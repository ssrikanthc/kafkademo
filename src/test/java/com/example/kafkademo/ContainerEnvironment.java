package com.process.builder;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.lang.ProcessBuilder.Redirect;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ContainerEnvironment {

	private static final Logger LOGGER = LoggerFactory.getLogger(ContainerEnvironment.class);

	private static ContainerEnvironment containerEnvironment;

	private ContainerEnvironment() {
	}

	public static ContainerEnvironment getInstance() {

		if (containerEnvironment == null) {

			synchronized (ContainerEnvironment.class) {

				if (containerEnvironment == null) {

					containerEnvironment = new ContainerEnvironment();
				}
			}
		}
		return containerEnvironment;
	}

	public CompletableFuture<String> build(String composeFilePath, int timeOutInSeconds) throws Exception {

		Executor executor = Executors.newFixedThreadPool(2);
		CompletableFuture<String> createContainersFuture = CompletableFuture.supplyAsync(() -> {

			List<String> commands = new ArrayList<>();
			commands.add("docker-compose");
			commands.add("-f");
			commands.add(composeFilePath);
			commands.add("up");

			ProcessBuilder builder = new ProcessBuilder();
			builder.command(commands);
			builder.redirectOutput(Redirect.PIPE);
			builder.redirectErrorStream(true);
			builder.redirectError(ProcessBuilder.Redirect.INHERIT);

			String result = "";

			Process dockerComposeCommand;

			try {

				dockerComposeCommand = builder.start();
				System.out.println("B4 wait..");
				dockerComposeCommand.waitFor(timeOutInSeconds / 60, TimeUnit.SECONDS);
				Thread.sleep(timeOutInSeconds*1000);
				System.out.println("Aftr wait..");
				try (BufferedReader reader = new BufferedReader(
						new InputStreamReader(dockerComposeCommand.getInputStream()))) {

					do {

						int c = reader.read();
						if (c > -1)
							result = result + (char) c;
						else
							break;
					} while (reader.ready());
					result = result + "DockerComposeUpCompleted";
					System.out.println("after result:" + result);
				}
			} catch (IOException | InterruptedException e) {
				LOGGER.error("ProcessBuilder Error:", e.getMessage());
			}
			return result;
		}, executor).thenApplyAsync(result -> {
			try {
				CompletableFuture<String> destroyContainersFuture = destroy(composeFilePath, timeOutInSeconds);
				LOGGER.info("destroyContainers status: " + destroyContainersFuture.toString());
			} catch (Exception e) {
				e.printStackTrace();
			}
			return result;
		});

		return createContainersFuture;
	}

	public CompletableFuture<String> destroy(String composeFilePath, int timeOutInSeconds) throws Exception {

		Executor executor = Executors.newFixedThreadPool(2);
		CompletableFuture<String> destroyContainersFuture = CompletableFuture.supplyAsync(() -> {

			String result = "";
			
			try {
				
				Thread.sleep(timeOutInSeconds * 1000);
				
				List<String> commands = new ArrayList<>();
				commands.add("docker-compose");
				commands.add("-f");
				commands.add(composeFilePath);
				commands.add("down");
				commands.add("--rmi");
				commands.add("all");
				commands.add("-v");
				
				ProcessBuilder builder = new ProcessBuilder();
				builder.command(commands);
				builder.redirectOutput(Redirect.PIPE);
				builder.redirectErrorStream(true);
				builder.redirectError(ProcessBuilder.Redirect.INHERIT);

				Process dockerComposeCommand = builder.start();
				System.out.println("B4 wait..");
				dockerComposeCommand.waitFor(timeOutInSeconds, TimeUnit.SECONDS);
				System.out.println("Aftr wait..");
				try (BufferedReader reader = new BufferedReader(
						new InputStreamReader(dockerComposeCommand.getInputStream()))) {

					do {

						int c = reader.read();
						if (c > -1)
							result = result + (char) c;
						else
							break;
					} while (reader.ready());
					result = result + "DockerComposeDownCompleted";
					System.out.println("after result:" + result);
				}
			} catch (Exception e) {
				LOGGER.error("ProcessBuilder Error:", e.getMessage());
			}

			return result;
		}, executor);

		return destroyContainersFuture;
	}

	public static void main(String[] args) {

		ContainerEnvironment ce = ContainerEnvironment.getInstance();

		try {

			CompletableFuture<String> cef = ce.destroy("C:/Workspace/STS/kafkademo/src/test/resources/docker-compose.yml",
					30);
			String result = cef.get();
			System.out.println("--IN MAIN--");
			System.out.println(result);
		} catch (Exception e) {

			e.printStackTrace();
		}
	}
}

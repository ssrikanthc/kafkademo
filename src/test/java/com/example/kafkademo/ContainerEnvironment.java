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

	public CompletableFuture<List<String>> build(String composeFilePath, int timeOutInSeconds) throws Exception {

		Executor executor = Executors.newFixedThreadPool(2);
		CompletableFuture<List<String>> createContainersFuture = CompletableFuture.supplyAsync(() -> {

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

			List<String> result = new ArrayList<>();

			Process dockerComposeCommand;

			try {

				dockerComposeCommand = builder.start();
				dockerComposeCommand.waitFor(timeOutInSeconds / 60, TimeUnit.SECONDS);
				try (BufferedReader reader = new BufferedReader(
						new InputStreamReader(dockerComposeCommand.getInputStream()))) {

					String line = "";
					do {
						
						line = reader.readLine();
						result.add(line);
					}while(reader.readLine() != null);
				}
			} catch (IOException | InterruptedException e) {
				LOGGER.error("ProcessBuilder Error:", e.getMessage());
			}
			return result;
		}, executor);

		try {
			Thread.sleep(timeOutInSeconds*1000);
			CompletableFuture<List<String>> destroyContainersFuture = destroy(createContainersFuture, composeFilePath, 0);
			LOGGER.info("destroyContainers status: " + destroyContainersFuture.toString());
		} catch (Exception e) {
			e.printStackTrace();
		}
		
		return createContainersFuture;
	}

	private CompletableFuture<List<String>> destroy(CompletableFuture<List<String>> createContainersFuture, String composeFilePath,
			int timeOutInSeconds) throws Exception {

		if(!createContainersFuture.isDone()) {
			createContainersFuture.completeExceptionally(new Throwable("Aborted due to timeout"));
		}
		
		return destroy(composeFilePath, timeOutInSeconds);
	}

	public CompletableFuture<List<String>> destroy(String composeFilePath, int timeOutInSeconds) throws Exception {

		Executor executor = Executors.newFixedThreadPool(2);
		CompletableFuture<List<String>> destroyContainersFuture = CompletableFuture.supplyAsync(() -> {

			List<String> result = new ArrayList<>();
			
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
				dockerComposeCommand.waitFor(timeOutInSeconds, TimeUnit.SECONDS);
				try (BufferedReader reader = new BufferedReader(
						new InputStreamReader(dockerComposeCommand.getInputStream()))) {

					String line = "";
					do {
						
						line = reader.readLine();
						result.add(line);
					}while(reader.ready());
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

			CompletableFuture<List<String>> cef = ce.destroy("C:/Workspace/STS/kafkademo/src/test/resources/docker-compose.yml",
					0);
			String result = cef.get().toString();
			System.out.println(result);
		} catch (Exception e) {

			e.printStackTrace();
		}
	}
}

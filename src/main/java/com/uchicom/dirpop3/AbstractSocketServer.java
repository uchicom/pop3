/**
 * (c) 2016 uchicom
 */
package com.uchicom.dirpop3;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 抽象ソケットサーバ.
 *
 * @author uchicom: Shigeki Uchiyama
 *
 */
public abstract class AbstractSocketServer implements Server {
	Pop3Parameter parameter;

	protected List<Pop3Process> processList = new CopyOnWriteArrayList<Pop3Process>();
	protected static Queue<ServerSocket> serverQueue = new ConcurrentLinkedQueue<ServerSocket>();

	public AbstractSocketServer(Pop3Parameter parameter) {
		this.parameter = parameter;
	}

	@Override
	public void execute() {
		// 接続強制クローズ処理
		Thread thread = new Thread() {
			public void run() {
				while (true) {
					for (Pop3Process process : processList) {
						if (System.currentTimeMillis() - process.getLastTime() > 10 * 1000) {
							process.forceClose();
							processList.remove(process);
						}
					}
					try {
						Thread.sleep(1000);
					} catch (InterruptedException e) {
						e.printStackTrace();
					}
				}
			}
		};
		thread.setDaemon(true);
		thread.start();
		try (ServerSocket serverSocket = new ServerSocket();) {
			serverSocket.setReuseAddress(true);
			serverSocket.bind(new InetSocketAddress(parameter.getPort()), parameter.getBacklog());
			serverQueue.add(serverSocket);
			execute(serverSocket);
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	protected abstract void execute(ServerSocket serverSocket) throws IOException;

	public static void shutdown(String[] args) {
		if (!serverQueue.isEmpty()) {
			try {
				serverQueue.poll().close();
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
	}
}

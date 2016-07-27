/**
 * (c) 2012 uchicom
 */
package com.uchicom.dirpop3;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;

/**
 * マルチスレッドのPOP3サーバー. new Threadを実施している.
 *
 * @author Uchiyama Shigeki
 *
 */
public class MultiPop3Server extends SinglePop3Server {

	/**
	 * アドレスとメールユーザーフォルダの格納フォルダを指定する
	 *
	 * @param args
	 */
	public static void main(String[] args) {
		Pop3Parameter parameter = new Pop3Parameter(args);
		if (parameter.init(System.err)) {
			MultiPop3Server server = new MultiPop3Server();
	    	server.execute(parameter);
		}
	}

	/**
	 * メイン処理
	 *
	 */
	private void execute(Pop3Parameter parameter) {
		ServerSocket serverSocket = null;
		try {
			serverSocket = new ServerSocket();
			serverSocket.setReuseAddress(true);
			serverSocket.bind(new InetSocketAddress(parameter.getPort()),
					parameter.getBack());
			serverQueue.add(serverSocket);
			Thread thread = new Thread() {
				public void run() {
					while(true) {
						for (Pop3Process process : processList) {
							if (System.currentTimeMillis() - process.getStartTime() > 10 * 1000) {
								process.forceClose();
								processList.remove(process);
							}
						}
						try {
							Thread.sleep(1000);
						} catch (InterruptedException e) {
							// TODO 自動生成された catch ブロック
							e.printStackTrace();
						}
					}
				}
			};
			thread.setDaemon(true);
			thread.start();
			while (true) {
				final Pop3Process process = new Pop3Process(parameter,
						serverSocket.accept());
				processList.add(process);
				new Thread() {
					public void run() {
						process.execute();
					}
				}.start();
			}
		} catch (IOException e) {
			e.printStackTrace();
		} finally {
			synchronized (serverSocket) {
				if (serverSocket != null) {
					try {
						serverSocket.close();
					} catch (Exception e) {
						e.printStackTrace();
					}
					serverSocket = null;
				}
			}
		}
	}

}

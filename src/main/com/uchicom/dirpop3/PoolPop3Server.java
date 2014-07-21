/**
 * (c) 2013 uchicom
 */
package com.uchicom.dirpop3;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * マルチスレッドのPOP3サーバー. スレッドプールを使用.
 * 
 * @author Uchiyama Shigeki
 * 
 */
public class PoolPop3Server extends SinglePop3Server {

	protected Socket socket;

	/**
	 * アドレスとメールユーザーフォルダの格納フォルダを指定する
	 * 
	 * @param args
	 */
	public static void main(String[] args) {
		Pop3Parameter parameter = new Pop3Parameter(args);
		if (parameter.init(System.err)) {
			execute(parameter);
		}
	}

	/**
	 * メイン処理
	 * 
	 */
	private static void execute(Pop3Parameter parameter) {

		ExecutorService exec = null;
		ServerSocket serverSocket = null;
		try {
			serverSocket = new ServerSocket();
			serverSocket.setReuseAddress(true);
			serverSocket.bind(new InetSocketAddress(parameter.getPort()),
					parameter.getBack());
			serverQueue.add(serverSocket);

			exec = Executors.newFixedThreadPool(parameter.getPool());
			while (true) {
				final Pop3Process process = new Pop3Process(parameter,
						serverSocket.accept());
				exec.execute(new Runnable() {
					@Override
					public void run() {
						process.execute();
					}
				});
			}
		} catch (IOException e) {
			e.printStackTrace();
		} finally {
			if (serverSocket != null) {
				try {
					serverSocket.close();
				} catch (Exception e) {
					e.printStackTrace();
				}
				serverSocket = null;
			}
			if (exec != null) {
				exec.shutdownNow();
			}
		}
	}

}

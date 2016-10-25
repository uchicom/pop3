/**
 * (c) 2012 uchicom
 */
package com.uchicom.dirpop3;

import java.io.IOException;
import java.net.ServerSocket;

import com.uchicom.server.AbstractSocketServer;
import com.uchicom.server.Parameter;

/**
 * マルチスレッドのPOP3サーバー. new Threadを実施している.
 *
 * @author Uchiyama Shigeki
 *
 */
public class MultiPop3Server extends AbstractSocketServer {

	/**
	 * @param parameter
	 */
	public MultiPop3Server(Parameter parameter) {
		super(parameter);
	}

	/**
	 * メイン処理
	 *
	 */
	@Override
	protected void execute(ServerSocket serverSocket) throws IOException {
		while (true) {
			final Pop3Process process = new Pop3Process(parameter,
					serverSocket.accept());
			processList.add(process);
			Thread thread = new Thread() {
				public void run() {
					process.execute();
				}
			};
			thread.setDaemon(true);
			thread.start();
		}
	}

}

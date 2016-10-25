/**
 * (c) 2013 uchicom
 */
package com.uchicom.dirpop3;

import java.io.IOException;
import java.net.ServerSocket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import com.uchicom.server.AbstractSocketServer;
import com.uchicom.server.Parameter;

/**
 * マルチスレッドのPOP3サーバー. スレッドプールを使用.
 *
 * @author Uchiyama Shigeki
 *
 */
public class PoolPop3Server extends AbstractSocketServer {

	ExecutorService exec = null;
	/**
	 * @param parameter
	 */
	public PoolPop3Server(Parameter parameter) {
		super(parameter);
		exec = Executors.newFixedThreadPool(parameter.getInt("pool"));
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
			exec.execute(new Runnable() {
				@Override
				public void run() {
					process.execute();
				}
			});
		}
	}

}

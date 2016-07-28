/**
 * (c) 2012 uchicom
 */
package com.uchicom.dirpop3;

import java.io.IOException;
import java.net.ServerSocket;

/**
 * POP3サーバー
 * シングルスレッド.
 * @author Uchiyama Shigeki
 *
 */
public class SinglePop3Server extends  AbstractSocketServer {

	public SinglePop3Server(Pop3Parameter parameter) {
		super(parameter);
	}
	/** メイン処理
	 *
	 */
	@Override
	protected void execute(ServerSocket serverSocket) throws IOException {
        while (true) {
            Pop3Process process = new Pop3Process(parameter, serverSocket.accept());
            process.execute();
        }
	}

}

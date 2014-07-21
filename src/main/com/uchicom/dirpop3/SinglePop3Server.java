/**
 * (c) 2012 uchicom
 */
package com.uchicom.dirpop3;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * POP3サーバー
 * シングルスレッド.
 * @author Uchiyama Shigeki
 * 
 */
public class SinglePop3Server {

	protected static Queue<ServerSocket> serverQueue = new ConcurrentLinkedQueue<ServerSocket>();

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
	/** メイン処理
	 * 
	 */
	private static void execute(Pop3Parameter parameter) {
	    
	    ServerSocket serverSocket = null;
        try {
        	serverSocket = new ServerSocket();
        	serverSocket.setReuseAddress(true);
            serverSocket.bind(new InetSocketAddress(parameter.getPort()), parameter.getBack());
            serverQueue.add(serverSocket);
            while (true) {
                Pop3Process process = new Pop3Process(parameter, serverSocket.accept());
                process.execute();
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

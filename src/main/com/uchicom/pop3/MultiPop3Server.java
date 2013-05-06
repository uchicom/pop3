/**
 * (c) 2012 uchicom
 */
package com.uchicom.pop3;

import java.io.File;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Date;

/**
 * マルチスレッドのPOP3サーバー.
 * new Threadを実施している.
 * @author Uchiyama Shigeki
 *
 */
public class MultiPop3Server extends SinglePop3Server implements Runnable {

    
    protected Socket socket;
    /**
     * アドレスとメールユーザーフォルダの格納フォルダを指定する
     * 
     * @param args
     */
    public static void main(String[] args) {
        if (args.length != 2) {
            System.out.println("args.length != 2");
            return;
        }
        // メールフォルダ格納フォルダ
        File file = new File(args[0]);
        if (!file.exists() || !file.isDirectory()) {
            System.out.println("mailbox directory is not found.");
            return;
        }

        // メール
        String hostName = args[1];

        // ポート
        int port = 8115;
        if (args.length > 2) {
            port = Integer.parseInt(args[2]);
        } 
        // 接続待ち数
        int back = 10;
        if (args.length == 3) {
            back = Integer.parseInt(args[3]);
        }

        execute(hostName, file, port, back);
        
        

    }
    
    public MultiPop3Server(String hostName, File file, Socket socket) {
        super(hostName, file);
        this.socket = socket;
    }

    /** メイン処理
     * 
     */
    private static void execute(String hostName, File file, int port, int back) {
        ServerSocket server = null;
        try {
            server = new ServerSocket();
            server.setReuseAddress(true);
            server.bind(new InetSocketAddress(8115), 10);
            serverQueue.add(server);
            while (true) {
                Socket socket = server.accept();
                System.out.println(format.format(new Date()) + ":"
                        + String.valueOf(socket.getRemoteSocketAddress()));
                new Thread(new MultiPop3Server(hostName, file, socket)).start();
            }
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            synchronized (server) {
                if (server != null) {
                    try {
                        server.close();
                    } catch (Exception e) {
                        e.printStackTrace();
                    } finally {
                        server = null;
                    }
                }
            }
        }
    }
    
    /* (non-Javadoc)
     * @see java.lang.Runnable#run()
     */
    @Override
    public void run() {
        pop3(socket);
    }

}

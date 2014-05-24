/**
 * (c) 2012 uchicom
 */
package com.uchicom.dirpop3;

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
public class MultiServer extends SingleServer implements Runnable {

    
    protected Socket socket;
    /**
     * アドレスとメールユーザーフォルダの格納フォルダを指定する
     * 
     * @param args
     */
    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("args.length < 1");
            return;
        }
        // メールフォルダ格納フォルダ
        File base = Pop3Static.DEFAULT_MAILBOX;

        if (args.length > 1) {
            base = new File(args[0]);
        }
        if (!base.exists() || !base.isDirectory()) {
            System.err.println("mailbox directory is not found.");
            return;
        }

        // メール
        String hostName = args[1];

        // ポート
        int port = Pop3Static.DEFAULT_PORT;
        if (args.length > 2) {
            port = Integer.parseInt(args[2]);
        } 
        // 接続待ち数
        int back =  Pop3Static.DEFAULT_BACK;
        if (args.length > 3) {
            back = Integer.parseInt(args[3]);
        }

        execute(hostName, base, port, back);
        
        

    }
    
    public MultiServer(String hostName, File file, Socket socket) {
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
            server.bind(new InetSocketAddress(port), back);
            serverQueue.add(server);
            while (true) {
                Socket socket = server.accept();
                if (Pop3Static.DEBUG) System.out.println(format.format(new Date()) + ":"
                        + String.valueOf(socket.getRemoteSocketAddress()));
                new Thread(new MultiServer(hostName, file, socket)).start();
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

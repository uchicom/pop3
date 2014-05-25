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
        Parameter param = new Parameter(args);
        if (param.init(System.err)) {
            execute(param);
        }
    }
    
    public MultiServer(String hostName, File file, Socket socket) {
        super(hostName, file);
        this.socket = socket;
    }

    /** メイン処理
     * 
     */
    private static void execute(Parameter param) {
        ServerSocket server = null;
        try {
            server = new ServerSocket();
            server.setReuseAddress(true);
            server.bind(new InetSocketAddress(param.getPort()), param.getBack());
            serverQueue.add(server);
            while (true) {
                Socket socket = server.accept();
                if (Pop3Static.DEBUG) System.out.println(format.format(new Date()) + ":"
                        + String.valueOf(socket.getRemoteSocketAddress()));
                new Thread(new MultiServer(param.getHostName(), param.getBase(), socket)).start();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        synchronized (server) {
            if (server != null) {
                try {
                    server.close();
                } catch (Exception e) {
                    e.printStackTrace();
                }
                server = null;
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

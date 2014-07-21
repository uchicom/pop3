/**
 * (c) 2014 uchicom
 */
package com.uchicom.dirpop3;

import java.io.File;
import java.io.PrintStream;

/**
 * @author uchicom: Shigeki Uchiyama
 *
 */
public class Pop3Parameter {
    private File base;
    private String hostName;
    private int port;
    private int back;
    private int pool;
    
    private String[] args;
    public Pop3Parameter(String[] args) {
        this.args = args;
    }
  
    /**
     * 初期化
     * @param ps
     * @return
     */
    public boolean init(PrintStream ps) {
        if (args.length < 1) {
            ps.println("args.length < 1");
            return false;
        }
        // メールフォルダ格納フォルダ
        base = Pop3Static.DEFAULT_MAILBOX;

        if (args.length > 1) {
            base = new File(args[0]);
        }
        if (!base.exists() || !base.isDirectory()) {
            System.err.println("mailbox directory is not found.");
            return false;
        }

        // メール
        hostName = args[1];

        // ポート
        port = Pop3Static.DEFAULT_PORT;
        if (args.length > 2) {
            port = Integer.parseInt(args[2]);
        } 
        // 接続待ち数
        back =  Pop3Static.DEFAULT_BACK;
        if (args.length > 3) {
            back = Integer.parseInt(args[3]);
        }
        // スレッドプール数
        pool = Pop3Static.DEFAULT_POOL;
        if (args.length > 4) {
            pool = Integer.parseInt(args[4]);
        }

        return true;
    }
    
    public String getHostName() {
        return hostName;
    }
    
    public int getPort() {
        return port;
    }
    public int getBack() {
        return back;
    }
    public File getBase() {
        return base;
    }
    public int getPool() {
        return pool;
    }
}

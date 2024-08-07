package com.itwray.study.advance.jvm;

public class Book {
    public static void main(String[] args)
    {
        /**
         * 书的普通代码块
         * 书的构造方法
         * price=110,amount=0
         * 书的静态代码块
         * 书的静态方法
         */
        staticFunction();
        /**
         * 书的普通代码块
         * 书的构造方法
         * price=110,amount=112
         */
        Book book1 = new Book();
        /**
         * 书的静态方法
         */
        staticFunction();
    }

    static Book book = new Book();

    static
    {
        System.out.println("书的静态代码块");
    }

    {
        System.out.println("书的普通代码块");
    }

    Book()
    {
        System.out.println("书的构造方法");
        System.out.println("price=" + price +",amount=" + amount);
    }

    public static void staticFunction(){
        System.out.println("书的静态方法");
    }

    int price = 110;
    static int amount = 112;
}

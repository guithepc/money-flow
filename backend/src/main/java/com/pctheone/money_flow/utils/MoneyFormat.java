package com.pctheone.money_flow.utils;


public class MoneyFormat {
    public static String format(String amount){
        return amount.replace(".", "").replace(",", ".");
    }
}

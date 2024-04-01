package com.ww.geo.region.bean;

/**
 * @author Zhanglele
 * @version 2023/5/16
 */
public class GeneratorRequestBean {
    private String regionSetting;
    private int level = 3;

    public String getRegionSetting() {
        return regionSetting;
    }

    public void setRegionSetting(String regionSetting) {
        this.regionSetting = regionSetting;
    }

    public int getLevel() {
        return level;
    }

    public void setLevel(int level) {
        this.level = level;
    }
}

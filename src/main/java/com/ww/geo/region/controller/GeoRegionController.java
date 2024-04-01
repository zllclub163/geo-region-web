package com.ww.geo.region.controller;

import com.alibaba.fastjson.JSONObject;
import com.ww.geo.region.bean.GeneratorRequestBean;
import com.ww.geo.region.generator.Region2GeoHashUtil;
import com.ww.geo.region.lookup.GeoTilesSearch;
import com.ww.geo.region.util.GeoHashHelper;
import org.apache.commons.lang3.StringUtils;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * @author Zhanglele
 * @version 2023/5/16
 */
@RestController
@RequestMapping("geo-region")
public class GeoRegionController {

    @PostMapping("generator")
    public List<String> regionSetting2GeoStr(@RequestBody GeneratorRequestBean requestBean) {
        return Region2GeoHashUtil.regionSetting2GeoStr(requestBean.getRegionSetting(), requestBean.getLevel());
    }

    @PostMapping("show/area")
    public Map<String, Object> showFenceGeoAreaBySetting(@RequestBody Map<String, String> body,
                                                         @RequestParam(name = "lngStart", required = false, defaultValue = "-180") Double lngStart,
                                                         @RequestParam(name = "lngEnd", required = false, defaultValue = "180") Double lngEnd,
                                                         @RequestParam(name = "latStart", required = false, defaultValue = "-90") Double latStart,
                                                         @RequestParam(name = "latEnd", required = false, defaultValue = "90") Double latEnd) {
        String setting = body.get("geos");
        if (StringUtils.isBlank(setting)) {
            return null;
        }

        String[] geoHash2 = setting.split(",");
        List<List<double[]>> left = new ArrayList<>();

        for (String s : geoHash2) {
            List<double[]> collect2 = GeoHashHelper.geoHash2Area(s);
            left.add(collect2);
        }
        List<List<double[]>> collect = new ArrayList<>();
        List<String> geos = new ArrayList<>();

        for (int i = 0; i < left.size(); i++) {
            List<double[]> lis = left.get(i);
            double[] min = lis.get(0);
            double[] max = lis.get(1);
            if (max[0] <= latEnd && max[0] >= latStart && max[1] <= lngEnd && max[1] >= lngStart) {
                collect.add(lis);
                geos.add(geoHash2[i]);
                continue;
            }
            if (min[0] <= latEnd && min[0] >= latStart && min[1] <= lngEnd && min[1] >= lngStart) {
                collect.add(lis);
                geos.add(geoHash2[i]);
            }
        }
        collect = collect.stream().limit(2000).collect(Collectors.toList());
        geos = geos.stream().limit(2000).collect(Collectors.toList());

        Map<String, Object> result = new HashMap<>();
        result.put("data", collect);
        result.put("geos", geos);
        result.put("geo7Count", geos.stream().map(String::length).filter(integer -> integer < 8).count());
        return result;
    }

    @PostMapping("fenceGeoZip/2/fenceGeo")
    public Map<String, Object> fenceGeoZip2FenceGeo(@RequestBody JSONObject body) {
        String fenceGeoZip = body.getString("fenceGeoZip");
        if (StringUtils.isBlank(fenceGeoZip)) {
            return null;
        }
        String[] geoStr = fenceGeoZip.split(",");
        List<String> allArea = new ArrayList<>();
        for (String s : geoStr) {
            String[] geo = s.split(":");
            String s1 = GeoTilesSearch.hex2Suffix(geo[1]);
            for (int i = 0; i < s1.length(); i++) {
                allArea.add(geo[0] + ":" + s1.charAt(i));
            }
        }
        Map<String, Object> result = new HashMap<>();
        result.put("allArea", String.join(",", allArea));
        return result;
    }

    @PostMapping("build/area")
    public Map<String, Object> buildFenceGeoAreaBySetting(@RequestBody JSONObject body) {
        String setting = body.getString("setting");
        if (StringUtils.isBlank(setting)) {
            return null;
        }
        List<String> geoStr = Region2GeoHashUtil.regionSetting2GeoStr(setting, 4);
        List<String> zipArea = new ArrayList<>();
        for (String s : geoStr) {
            String[] geo = s.split(":");
            zipArea.add(geo[0] + ":" + GeoTilesSearch.suffix2Hex(geo[1]));
        }
        List<String> elements = repeatedlyDeduplication(zipArea);
        Map<String, Object> result = new HashMap<>();
        result.put("allArea", String.join(",", deduplication(elements)));
        result.put("zipArea", String.join(",", elements));
        return result;
    }

    //反复去重
    public static List<String> repeatedlyDeduplication(Collection<String> geos) {
        geos = compressSuffix(margeGeos(deduplication(geos)));
        geos = compressSuffix(margeGeos(deduplication(geos)));
        geos = compressSuffix(margeGeos(deduplication(geos)));
        return compressSuffix(margeGeos(deduplication(geos)));
    }

    //把u3sd:sdf2345678rtyu->u3sd:ffff 压缩
    public static List<String> compressSuffix(Collection<String> geos) {
        return geos.stream().map(s -> {
            String[] split = s.split(":");
            return split[0] + ":" + GeoTilesSearch.suffix2Hex(split[1]);
        }).collect(Collectors.toList());
    }

    //把[u3sd1,u3sd2,u3sdd,u3sds,u3sdf]->u3sd:12dsf合并
    public static List<String> margeGeos(Collection<String> geos) {
        Map<Integer, List<String>> collect = geos.stream().collect(Collectors.groupingBy(String::length));
        List<String> result = new ArrayList<>();
        for (List<String> value : collect.values()) {
            Map<String, List<String>> collect1 = value.stream().collect(Collectors.groupingBy(s -> s.substring(0, s.length() - 1)));
            for (Map.Entry<String, List<String>> entry : collect1.entrySet()) {
                String key = entry.getKey();
                List<String> list = entry.getValue();
                String collect2 = list.stream().map(s -> s.substring(s.length() - 1)).collect(Collectors.joining());
                result.add(key + ":" + collect2);
            }
        }
        return result;
    }

    //把u3sd:ffff->[把u3sd]去冗余
    //把u3sd:12dsf ->[u3sd1,u3sd2,u3sdd,u3sds,u3sdf]展开
    public static List<String> deduplication(Collection<String> geos) {
        return geos.stream().map(s -> {
            String[] split = s.split(":");
            List<String> list = new ArrayList<>();
            if (split[1].equals("ffffffff")) {
                list.add(split[0]);
                return list;
            }
            split[1] = GeoTilesSearch.hex2Suffix(split[1]);
            for (int i = 0; i < split[1].length(); i++) {
                list.add(split[0] + split[1].charAt(i));
            }
            return list;
        }).flatMap(Collection::stream).distinct().collect(Collectors.toList());
    }

}

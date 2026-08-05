/*
 * Copyright 2024-2025 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.wuji.assistant.mcp.tools;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Service;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;

/**
 * @author yingzi
 * @since 2025/10/22
 */
@Service
public class TimeService {

    private static final Logger logger = LoggerFactory.getLogger(TimeService.class);

    @Tool(name = "get_city_time", description = "获取指定城市的当前时间。")
    public String  getCityTimeMethod(@ToolParam(description = "Time zone id, such as Asia/Shanghai") String timeZoneId) {
        logger.info("The current time zone is {}", timeZoneId);
        return String.format("The current time zone is %s and the current time is " + "%s", timeZoneId,
                getTimeByZoneId(timeZoneId));
    }

    private String getTimeByZoneId(String zoneId) {

        // Get the time zone using ZoneId
        ZoneId zid = ZoneId.of(zoneId);

        // Get the current time in this time zone
        ZonedDateTime zonedDateTime = ZonedDateTime.now(zid);

        // Defining a formatter
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss z");

        // Format ZonedDateTime as a string
        String formattedDateTime = zonedDateTime.format(formatter);

        return formattedDateTime;
    }

    /**
     * 查询指定城市的旅游景点。
     *
     * @param placeName 城市名称，如：北京
     * @return 景点信息
     */
    @Tool(name = "get_city_tourist_spots", description = "查询指定城市的旅游景点。")
    public String getCityTouristSpotsMethod(@ToolParam(description = "城市名称，如：北京") String placeName) {
        logger.info("待查询的地名是 {}", placeName);
        return String.format("地名：%s 旅游景点有：%s", placeName, "两个黄鹂鸣翠柳，一行白鹭上青天");
    }

    /**
     * 根据地点和时间查询当地可以吃到的食物。
     *
     * @param placeName 地点名称，如：北京
     * @param hour 小时（0-23）
     * @return 食物信息
     */
    @Tool(name = "get_food", description = "根据地点和时间(精确到小时)查询当地可以吃到的食物。")
    public String getFoodMethod(@ToolParam(description = "地点名称，如：北京") String placeName,
            @ToolParam(description = "小时（0-23）") Integer hour) {
        logger.info("待查询的地名是 {}，当前时间是：{}", placeName, hour);
        String food;
        if (hour >= 6 && hour < 9) {
            food = "豆浆油条、包子";
        } else if (hour >= 11 && hour < 13) {
            food = "面条、米饭、炒菜";
        } else if (hour >= 18 && hour < 21) {
            food = "火锅、烧烤、炒菜";
        } else {
            food = "零食、水果";
        }
        return String.format("地名：%s，时间：%d 点，可以吃到的食物有：%s", placeName, hour, food);
    }
}

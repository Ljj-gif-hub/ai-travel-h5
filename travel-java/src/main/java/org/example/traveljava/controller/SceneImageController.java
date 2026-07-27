package org.example.traveljava.controller;

import org.example.traveljava.dto.SceneImageDTO;
import org.example.traveljava.service.SceneImageService;
import org.example.traveljava.vo.Result;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 景点图片接口
 * 
 * 接口说明：
 * GET /api/scene/image
 * 请求参数：
 *   scenicName: 景点名称（如天坛公园）
 *   scenicDesc: 景点简短介绍（可选）
 * 
 * 返回结果：
 * {
 *     "code": 200,
 *     "data": {
 *         "imgUrl": "https://xxx.jpg",
 *         "source": "map|ai|default"  // 标记图片来源，方便调试
 *     }
 * }
 * 
 * 实现逻辑：
 * 1. 优先级1：调用百度地图POI详情接口获取官方实景图片
 * 2. 优先级2：调用AI绘画接口生成图片
 * 3. 优先级3：返回默认占位图
 */
@RestController
@RequestMapping("/api/scene")
public class SceneImageController {

    private static final Logger log = LoggerFactory.getLogger(SceneImageController.class);

    private final SceneImageService sceneImageService;

    public SceneImageController(SceneImageService sceneImageService) {
        this.sceneImageService = sceneImageService;
    }

    /**
     * 获取景点图片
     * 
     * @param scenicName 景点名称（必填）
     * @param scenicDesc 景点描述（可选）
     * @return 图片信息
     */
    @GetMapping("/image")
    public Result<SceneImageDTO> getSceneImage(
            @RequestParam(required = false) String scenicName,
            @RequestParam(required = false) String scenicDesc) {
        
        log.info("获取景点图片请求: scenicName=[{}], scenicDesc=[{}]", 
                scenicName == null ? "null" : scenicName, 
                scenicDesc == null ? "null" : scenicDesc);
        
        if (scenicName == null || scenicName.trim().isEmpty()) {
            log.warn("获取景点图片失败: scenicName参数为空");
            return Result.fail("景点名称不能为空");
        }
        
        try {
            SceneImageDTO image = sceneImageService.getSceneImage(scenicName.trim(), scenicDesc);
            log.info("获取景点图片成功: source={}, url={}", image.getSource(), image.getImgUrl());
            return Result.ok(image);
        } catch (Exception e) {
            log.error("获取景点图片失败: scenicName={}", scenicName, e);
            return Result.fail("获取图片失败：" + e.getMessage());
        }
    }
}
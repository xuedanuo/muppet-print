package com.xuesinuo.muppet.config;

import java.util.Map;
import java.util.UUID;

import org.springframework.stereotype.Component;

import com.xuesinuo.muppet.UiStarter;
import com.xuesinuo.muppet.api.VersionApi;
import com.xuesinuo.muppet.config.exceptions.ParamException;
import com.xuesinuo.muppet.config.exceptions.ServiceException;
import com.xuesinuo.xtool.Np;

import io.vertx.core.json.Json;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.client.WebClient;
import io.vertx.ext.web.handler.BodyHandler;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@RequiredArgsConstructor
public class ApiRootVerticle {

    private final Router router;
    private final WebClient webClient;

    @PostConstruct
    public void start() {
        router.route("/api/*").order(Integer.MIN_VALUE)
                .handler(BodyHandler.create().setBodyLimit(1024 * 1024))// 请求体大小限制
                .handler(http -> {
                    http.response().setChunked(true).putHeader("Content-Type", "application/json");
                    log.info("API => " + http.request().uri());
                    http.next();
                });

        router.route("/api/*").order(Integer.MAX_VALUE)
                .handler(http -> {
                    http.end();
                });

        router.route("/api/*").order(Integer.MIN_VALUE)
                .failureHandler(http -> {
                    ApiResult<?> apiResult = new ApiResult<>();
                    Throwable t = http.failure();
                    if (t != null) {
                        if (t instanceof ParamException) {
                            apiResult.setCode(ApiResultCode.PARAM_ERROR);
                            apiResult.setMessage("ParamException: " + t.getMessage());
                            http.response().setStatusCode(200).send(Json.encode(apiResult));
                            return;
                        }
                        if (t instanceof ServiceException) {
                            apiResult.setCode(ApiResultCode.SERVICE_ERROR);
                            apiResult.setMessage("ServiceException: " + t.getMessage());
                            http.response().setStatusCode(200).send(Json.encode(apiResult));
                            return;
                        }
                    }
                    String errorId = UUID.randomUUID().toString().substring(0, 8);
                    StringBuilder logBuilder = new StringBuilder();
                    logBuilder.append("MuppetApi error [" + errorId + "]");
                    logBuilder.append(t.getMessage()).append("\n");
                    for (StackTraceElement element : t.getStackTrace()) {
                        logBuilder.append(element.toString()).append("\n");
                    }
                    webClient.post(443, "test-wms.foodsup.com", "/api/wms/muppetPrintLog").ssl(true)
                            .sendJson(Map.of(
                                    "token", "452e9c36209d4795a9c5304538f490c1",
                                    "level", "error",
                                    "version", VersionApi.VERSION,
                                    "message", logBuilder.toString()))
                            .onFailure(error -> UiStarter.error("send error log failed."));
                    apiResult.setCode(ApiResultCode.SYSTEM_ERROR);
                    apiResult.setMessage("System error (" + errorId + ").");
                    http.response().setStatusCode(500).send(Json.encode(apiResult));
                });
        // router.errorHandler(500, http -> {});
    }
}

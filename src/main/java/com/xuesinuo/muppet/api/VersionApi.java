package com.xuesinuo.muppet.api;

import java.util.HashMap;

import org.springframework.stereotype.Component;

import com.xuesinuo.muppet.UiStarter;
import com.xuesinuo.muppet.config.ApiResult;
import com.xuesinuo.xtool.Np;

import io.vertx.ext.web.Router;
import io.vertx.ext.web.client.WebClient;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class VersionApi {
    private final Router router;
    private final WebClient webClient;

    @PostConstruct
    public void start() {
        uiVersion();
        getVersion();
    }

    /** 当前版本 */
    public static final String VERSION = "1.0.1";

    private void uiVersion() {
        webClient.get(443, "www.xuesinuo.com", "/muppet-print/version").ssl(true).send()
                .onSuccess(resp -> {
                    if (Np.i(resp.bodyAsString()).notEq(VERSION)) {
                        UiStarter.error("new version: https://github.com/xsnuo/muppet-print/releases");
                    }
                });
    }

    /** 程序版本号 */
    private void getVersion() {
        router.route("/api/version").handler(http -> {
            HashMap<String, Object> data = new HashMap<>();
            data.put("version", VERSION);
            webClient.get(443, "www.xuesinuo.com", "/muppet-print/version").ssl(true).send()
                    .onSuccess(resp -> {
                        data.put("newVersion", resp.bodyAsString());
                    })
                    .onFailure(error -> {
                        error.printStackTrace();
                    })
                    .onComplete(resp -> {
                        http.response().write(ApiResult.ok(data));
                        http.next();
                    });

        });
    }
}

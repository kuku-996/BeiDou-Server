package org.gms.controller;

import lombok.AllArgsConstructor;
import org.gms.constants.api.ApiConstant;
import org.gms.model.dto.ResultBody;
import org.gms.model.dto.SubmitBody;
import org.gms.server.DailyCheckinConfig;
import org.gms.server.DailyCheckinConfigService;
import org.gms.server.setitem.ItemSetAdminConfig;
import org.gms.server.setitem.ItemSetConfigService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Web-console API for daily check-in and item-set configuration. */
@RestController
@AllArgsConstructor
@RequestMapping("/gameFeature")
public class GameFeatureConfigController {
    private final DailyCheckinConfigService dailyCheckinConfigService;
    private final ItemSetConfigService itemSetConfigService;

    @GetMapping("/" + ApiConstant.LATEST + "/dailyCheckin")
    public ResultBody<DailyCheckinConfig> getDailyCheckin() {
        return ResultBody.success(dailyCheckinConfigService.getConfig());
    }

    @PostMapping("/" + ApiConstant.LATEST + "/dailyCheckin")
    public ResultBody<DailyCheckinConfig> saveDailyCheckin(
            @RequestBody SubmitBody<DailyCheckinConfig> request) {
        return ResultBody.success(request, dailyCheckinConfigService.save(request.getData()));
    }

    @PostMapping("/" + ApiConstant.LATEST + "/dailyCheckin/reset")
    public ResultBody<DailyCheckinConfig> resetDailyCheckin() {
        return ResultBody.success(dailyCheckinConfigService.reset());
    }

    @GetMapping("/" + ApiConstant.LATEST + "/itemSets")
    public ResultBody<ItemSetAdminConfig> getItemSets() {
        return ResultBody.success(itemSetConfigService.getConfig());
    }

    @PostMapping("/" + ApiConstant.LATEST + "/itemSets")
    public ResultBody<ItemSetAdminConfig> saveItemSets(
            @RequestBody SubmitBody<ItemSetAdminConfig> request) {
        return ResultBody.success(request, itemSetConfigService.save(request.getData()));
    }

    @PostMapping("/" + ApiConstant.LATEST + "/itemSets/resetWz")
    public ResultBody<ItemSetAdminConfig> resetItemSetsToWz() {
        return ResultBody.success(itemSetConfigService.resetToWz());
    }
}

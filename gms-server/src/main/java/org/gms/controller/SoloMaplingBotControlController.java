package org.gms.controller;

import lombok.AllArgsConstructor;
import org.gms.constants.api.ApiConstant;
import org.gms.model.dto.ResultBody;
import org.gms.model.dto.SubmitBody;
import org.gms.server.artificial.soloport.control.SoloMaplingBotControlConfig;
import org.gms.server.artificial.soloport.control.SoloMaplingBotControlService;
import org.gms.server.artificial.soloport.control.SoloMaplingBotControlStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Web-console API for live SoloMapling population control. */
@RestController
@AllArgsConstructor
@RequestMapping("/solomapling")
public class SoloMaplingBotControlController {
    private final SoloMaplingBotControlService botControlService;

    @GetMapping("/" + ApiConstant.LATEST + "/botControl")
    public ResultBody<SoloMaplingBotControlStatus> getStatus() {
        return ResultBody.success(botControlService.getStatus());
    }

    @PostMapping("/" + ApiConstant.LATEST + "/botControl")
    public ResultBody<SoloMaplingBotControlStatus> save(
            @RequestBody SubmitBody<SoloMaplingBotControlConfig> request) {
        return ResultBody.success(request, botControlService.save(request.getData()));
    }

    @PostMapping("/" + ApiConstant.LATEST + "/botControl/reconcile")
    public ResultBody<SoloMaplingBotControlStatus> reconcile() {
        return ResultBody.success(botControlService.reconcileNow());
    }
}

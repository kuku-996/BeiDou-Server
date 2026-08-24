package org.gms.controller;

import lombok.AllArgsConstructor;
import org.gms.constants.api.ApiConstant;
import org.gms.model.dto.ResultBody;
import org.gms.model.dto.SubmitBody;
import org.gms.server.artificial.soloport.localization.SoloMaplingChatPhraseService;
import org.gms.server.artificial.soloport.localization.SoloMaplingAiConfig;
import org.gms.server.artificial.soloport.localization.SoloMaplingAiSpeechService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/** Web-console API for live editing of categorized Chinese bot phrase books. */
@RestController
@AllArgsConstructor
@RequestMapping("/solomapling")
public class SoloMaplingChatController {
    private final SoloMaplingChatPhraseService chatPhraseService;
    private final SoloMaplingAiSpeechService aiSpeechService;

    @GetMapping("/" + ApiConstant.LATEST + "/chatPhrases")
    public ResultBody<List<String>> getChatPhrases() {
        return ResultBody.success(chatPhraseService.getPhrases());
    }

    @PostMapping("/" + ApiConstant.LATEST + "/chatPhrases")
    public ResultBody<List<String>> saveChatPhrases(@RequestBody SubmitBody<List<String>> request) {
        return ResultBody.success(request, chatPhraseService.savePhrases(request.getData()));
    }

    @GetMapping("/" + ApiConstant.LATEST + "/chatPhraseBook")
    public ResultBody<Map<String, List<String>>> getChatPhraseBook() {
        return ResultBody.success(chatPhraseService.getPhraseBook());
    }

    @PostMapping("/" + ApiConstant.LATEST + "/chatPhraseBook")
    public ResultBody<Map<String, List<String>>> saveChatPhraseBook(
            @RequestBody SubmitBody<Map<String, List<String>>> request) {
        return ResultBody.success(request, chatPhraseService.savePhraseBook(request.getData()));
    }

    @GetMapping("/" + ApiConstant.LATEST + "/aiChatConfig")
    public ResultBody<SoloMaplingAiConfig> getAiChatConfig() {
        return ResultBody.success(aiSpeechService.getClientConfig());
    }

    @PostMapping("/" + ApiConstant.LATEST + "/aiChatConfig")
    public ResultBody<SoloMaplingAiConfig> saveAiChatConfig(
            @RequestBody SubmitBody<SoloMaplingAiConfig> request) {
        return ResultBody.success(request, aiSpeechService.saveClientConfig(request.getData()));
    }

    @PostMapping("/" + ApiConstant.LATEST + "/aiChatConfig/test")
    public ResultBody<String> testAiChatConfig() {
        return ResultBody.success(aiSpeechService.testConnection());
    }
}

package com.khang.jobtrack.controller;

import com.khang.jobtrack.ai.AiException;
import com.khang.jobtrack.ai.InterviewQuestion;
import com.khang.jobtrack.entity.User;
import com.khang.jobtrack.service.AnswerReviewService;
import com.khang.jobtrack.service.ApplicationService;
import com.khang.jobtrack.service.InterviewQuestionService;
import com.khang.jobtrack.service.UserService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;

/**
 * 面接の想定質問と、回答の添削
 *   GET  /applications/{id}/ai-questions          これまでの面接と「質問を作る」ボタン
 *   POST /applications/{id}/ai-questions          想定質問を作る（excluded があれば「別の質問」）
 *   POST /applications/{id}/ai-questions/review   自分の回答をAIが添削
 * どれも保存はしない。AIの結果は ai_results にキャッシュされる。
 */
@Controller
@RequestMapping("/applications/{id}/ai-questions")
public class AiQuestionController {

    private final UserService userService;
    private final ApplicationService applicationService;
    private final InterviewQuestionService interviewQuestionService;
    private final AnswerReviewService answerReviewService;

    public AiQuestionController(UserService userService, ApplicationService applicationService,
                                InterviewQuestionService interviewQuestionService,
                                AnswerReviewService answerReviewService) {
        this.userService = userService;
        this.applicationService = applicationService;
        this.interviewQuestionService = interviewQuestionService;
        this.answerReviewService = answerReviewService;
    }

    @GetMapping
    public String show(@AuthenticationPrincipal UserDetails userDetails, @PathVariable Long id, Model model) {
        User user = userService.findByEmail(userDetails.getUsername());
        addCommon(model, user, id);
        return "ai/questions";
    }

    /**
     * @param excluded 「別の質問を作る」のとき、これまでに出た質問（画面の hidden から送られる）
     */
    @PostMapping
    public String generate(@AuthenticationPrincipal UserDetails userDetails, @PathVariable Long id,
                           @RequestParam(required = false) List<String> excluded, Model model) {
        User user = userService.findByEmail(userDetails.getUsername());
        addCommon(model, user, id);

        List<String> cleaned = InterviewQuestionService.cleanExcluded(excluded);
        try {
            List<InterviewQuestion> questions = interviewQuestionService.generate(user, id, cleaned);
            model.addAttribute("questions", questions);

            // 次に「別の質問を作る」を押したときに送る一覧 = これまでの質問 + 今回の質問
            List<String> nextExcluded = new ArrayList<>(cleaned);
            questions.forEach(q -> nextExcluded.add(q.question()));
            model.addAttribute("nextExcluded", InterviewQuestionService.cleanExcluded(nextExcluded));
        } catch (AiException e) {
            model.addAttribute("aiError", e.getMessage());
        }
        return "ai/questions";
    }

    @PostMapping("/review")
    public String review(@AuthenticationPrincipal UserDetails userDetails, @PathVariable Long id,
                         @RequestParam String question,
                         @RequestParam(required = false) String intent,
                         @RequestParam(required = false) String answer, Model model) {
        User user = userService.findByEmail(userDetails.getUsername());
        model.addAttribute("jobApplication", applicationService.findOwned(user, id)); // 他人の応募なら404
        model.addAttribute("question", question);
        model.addAttribute("intent", intent);
        model.addAttribute("answer", answer);
        model.addAttribute("maxAnswerLength", AnswerReviewService.MAX_ANSWER_LENGTH);

        try {
            model.addAttribute("review", answerReviewService.review(user, id, question, intent, answer));
        } catch (AiException e) {
            model.addAttribute("aiError", e.getMessage());
        }
        return "ai/review";
    }

    private void addCommon(Model model, User user, Long id) {
        model.addAttribute("jobApplication", applicationService.findOwned(user, id)); // 他人の応募なら404
        model.addAttribute("interviews", interviewQuestionService.findInterviews(user, id));
    }
}

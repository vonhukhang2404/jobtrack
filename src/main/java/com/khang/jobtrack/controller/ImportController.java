package com.khang.jobtrack.controller;

import com.khang.jobtrack.ai.AiException;
import com.khang.jobtrack.dto.CompanyImportForm;
import com.khang.jobtrack.entity.User;
import com.khang.jobtrack.importer.DocumentReadException;
import com.khang.jobtrack.importer.DocxTextExtractor;
import com.khang.jobtrack.service.CompanyImportService;
import com.khang.jobtrack.service.UserService;
import org.springframework.beans.propertyeditors.StringTrimmerEditor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;

/**
 * 資料の取り込み（説明会の案内・求人情報 → 企業・応募・説明会タスク）
 *   GET  /imports           ファイル（.docx / .txt）を選ぶか、文章を貼り付ける
 *   POST /imports/extract   AIで企業ごとに整理して、確認画面を表示
 *   POST /imports           チェックした企業を登録
 */
@Controller
@RequestMapping("/imports")
public class ImportController {

    private final UserService userService;
    private final CompanyImportService companyImportService;
    private final DocxTextExtractor docxTextExtractor;

    public ImportController(UserService userService, CompanyImportService companyImportService,
                            DocxTextExtractor docxTextExtractor) {
        this.userService = userService;
        this.companyImportService = companyImportService;
        this.docxTextExtractor = docxTextExtractor;
    }

    @InitBinder("companyImportForm")
    public void initBinder(WebDataBinder binder) {
        binder.registerCustomEditor(String.class, new StringTrimmerEditor(true));
    }

    @GetMapping
    public String show(Model model) {
        model.addAttribute("maxLength", CompanyImportService.MAX_TEXT_LENGTH);
        return "imports/index";
    }

    @PostMapping("/extract")
    public String extract(@AuthenticationPrincipal UserDetails userDetails,
                          @RequestParam(required = false) MultipartFile file,
                          @RequestParam(required = false) String text, Model model) {
        User user = userService.findByEmail(userDetails.getUsername());
        model.addAttribute("maxLength", CompanyImportService.MAX_TEXT_LENGTH);

        try {
            String source = readSource(file, text);
            model.addAttribute("text", source); // 確認画面でも原文を見られるように
            CompanyImportService.ImportPreview preview = companyImportService.extract(user, source, LocalDate.now());
            CompanyImportForm form = new CompanyImportForm();
            form.setItems(preview.items());
            model.addAttribute("companyImportForm", form);
            model.addAttribute("importWarning", preview.warning());
        } catch (DocumentReadException | AiException e) {
            model.addAttribute("text", text);
            model.addAttribute("importError", e.getMessage());
        }
        return "imports/index";
    }

    @PostMapping
    public String save(@AuthenticationPrincipal UserDetails userDetails,
                       @ModelAttribute CompanyImportForm companyImportForm, BindingResult bindingResult,
                       Model model, RedirectAttributes redirectAttributes) {
        User user = userService.findByEmail(userDetails.getUsername());

        // チェックした行だけ入力チェック
        List<CompanyImportForm.Item> items = companyImportForm.getItems();
        boolean anySelected = false;
        for (int i = 0; i < items.size(); i++) {
            CompanyImportForm.Item item = items.get(i);
            if (!item.isSelected()) {
                continue;
            }
            anySelected = true;
            if (item.getName() == null) {
                bindingResult.rejectValue("items[" + i + "].name", "required", "企業名を入力してください");
            } else if (item.getName().length() > 100) {
                bindingResult.rejectValue("items[" + i + "].name", "size", "100文字以内で入力してください");
            }
            if (item.getIndustry() != null && item.getIndustry().length() > 50) {
                bindingResult.rejectValue("items[" + i + "].industry", "size", "50文字以内で入力してください");
            }
            if (item.getPosition() == null) {
                bindingResult.rejectValue("items[" + i + "].position", "required", "職種を入力してください");
            } else if (item.getPosition().length() > 100) {
                bindingResult.rejectValue("items[" + i + "].position", "size", "100文字以内で入力してください");
            }
            if (item.getTaskTitle() != null && item.getTaskTitle().length() > 100) {
                bindingResult.rejectValue("items[" + i + "].taskTitle", "size", "100文字以内で入力してください");
            }
        }
        if (!anySelected) {
            model.addAttribute("importError", "登録する企業にチェックしてください。");
        }
        if (bindingResult.hasErrors() || !anySelected) {
            model.addAttribute("maxLength", CompanyImportService.MAX_TEXT_LENGTH);
            return "imports/index";
        }

        CompanyImportService.ImportResult result = companyImportService.save(user, companyImportForm);
        redirectAttributes.addFlashAttribute("message",
                "資料から取り込みました：企業 " + result.companiesCreated() + " 社（既存 " + result.companiesReused()
                        + " 社）、応募 " + result.applicationsCreated() + " 件（既存 " + result.applicationsReused()
                        + " 件）、説明会タスク " + result.tasksCreated() + " 件");
        return "redirect:/applications";
    }

    // ファイルがあればファイルを、なければ貼り付けた文章を使う
    private String readSource(MultipartFile file, String text) {
        if (file != null && !file.isEmpty()) {
            String name = file.getOriginalFilename() == null ? "" : file.getOriginalFilename().toLowerCase(Locale.ROOT);
            try {
                if (name.endsWith(".docx")) {
                    return docxTextExtractor.extract(file.getInputStream());
                }
                if (name.endsWith(".txt")) {
                    return new String(file.getBytes(), StandardCharsets.UTF_8);
                }
            } catch (IOException e) {
                throw new DocumentReadException("ファイルを読み込めませんでした。", e);
            }
            throw new DocumentReadException("対応しているファイルは .docx と .txt です。");
        }
        if (text == null || text.isBlank()) {
            throw new DocumentReadException("ファイルを選ぶか、文章を貼り付けてください。");
        }
        return text;
    }
}

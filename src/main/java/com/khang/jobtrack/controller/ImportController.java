package com.khang.jobtrack.controller;

import com.khang.jobtrack.ai.AiException;
import com.khang.jobtrack.dto.CompanyImportForm;
import com.khang.jobtrack.entity.User;
import com.khang.jobtrack.importer.DocumentReadException;
import com.khang.jobtrack.importer.DocxTextExtractor;
import com.khang.jobtrack.service.CompanyImportService;
import com.khang.jobtrack.service.UserService;
import org.springframework.beans.propertyeditors.StringTrimmerEditor;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
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
    private final MessageSource messageSource;

    public ImportController(UserService userService, CompanyImportService companyImportService,
                            DocxTextExtractor docxTextExtractor, MessageSource messageSource) {
        this.userService = userService;
        this.companyImportService = companyImportService;
        this.docxTextExtractor = docxTextExtractor;
        this.messageSource = messageSource;
    }

    // 画面の言語（Cookie / ?lang=）に合わせた文言を messages*.properties から取得
    private String msg(String code, Object... args) {
        return messageSource.getMessage(code, args, LocaleContextHolder.getLocale());
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
                bindingResult.rejectValue("items[" + i + "].name", "valid.companyName.required");
            } else if (item.getName().length() > 100) {
                bindingResult.rejectValue("items[" + i + "].name", "valid.max100");
            }
            if (item.getIndustry() != null && item.getIndustry().length() > 50) {
                bindingResult.rejectValue("items[" + i + "].industry", "valid.max50");
            }
            if (item.getPosition() == null) {
                bindingResult.rejectValue("items[" + i + "].position", "valid.position.required");
            } else if (item.getPosition().length() > 100) {
                bindingResult.rejectValue("items[" + i + "].position", "valid.max100");
            }
            if (item.getTaskTitle() != null && item.getTaskTitle().length() > 100) {
                bindingResult.rejectValue("items[" + i + "].taskTitle", "valid.max100");
            }
        }
        if (!anySelected) {
            model.addAttribute("importError", msg("import.error.noneSelected"));
        }
        if (bindingResult.hasErrors() || !anySelected) {
            model.addAttribute("maxLength", CompanyImportService.MAX_TEXT_LENGTH);
            return "imports/index";
        }

        CompanyImportService.ImportResult result = companyImportService.save(user, companyImportForm);
        redirectAttributes.addFlashAttribute("message", msg("import.done",
                result.companiesCreated(), result.companiesReused(),
                result.applicationsCreated(), result.applicationsReused(), result.tasksCreated()));
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
                throw new DocumentReadException(msg("import.error.read"), e);
            }
            throw new DocumentReadException(msg("import.error.type"));
        }
        if (text == null || text.isBlank()) {
            throw new DocumentReadException(msg("import.error.empty"));
        }
        return text;
    }
}

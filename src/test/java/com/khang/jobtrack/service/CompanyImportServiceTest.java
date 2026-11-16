package com.khang.jobtrack.service;

import com.khang.jobtrack.ai.AiClient;
import com.khang.jobtrack.ai.AiException;
import com.khang.jobtrack.dto.CompanyImportForm;
import com.khang.jobtrack.entity.Application;
import com.khang.jobtrack.entity.ApplicationStatus;
import com.khang.jobtrack.entity.Company;
import com.khang.jobtrack.entity.Task;
import com.khang.jobtrack.entity.User;
import com.khang.jobtrack.repository.ApplicationRepository;
import com.khang.jobtrack.repository.CompanyRepository;
import com.khang.jobtrack.repository.TaskRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * 資料の取り込みのテスト（本物のAI・DBは使わない）。
 */
@ExtendWith(MockitoExtension.class)
class CompanyImportServiceTest {

    @Mock
    AiClient aiClient;

    @Mock
    CompanyRepository companyRepository;

    @Mock
    ApplicationRepository applicationRepository;

    @Mock
    TaskRepository taskRepository;

    @InjectMocks
    CompanyImportService service;

    private static final LocalDate TODAY = LocalDate.of(2026, 11, 16);

    private static final String TWO_COMPANIES = """
            {"totalCount":2,"companies":[
              {"name":"DMM.com","industry":"IT","positions":["Webエンジニア","インフラ"],"japanese":"N2以上",
               "foreignStudentNote":"","location":"東京","eventDate":"2026-12-09","eventTime":"11:00",
               "eventFormat":"オンライン","features":["大規模サービス","勉強会が多い"]},
              {"name":"KDDIアイレット","industry":"クラウド","positions":["クラウドインフラエンジニア"],"japanese":"",
               "foreignStudentNote":"AWSの学習経験が必要","location":"大阪","eventDate":"2026-12-15","eventTime":"16:00",
               "eventFormat":"対面","features":[]}
            ]}""";

    // ---------- buildPrompt ----------

    @Test
    @DisplayName("buildPrompt: 資料・今日の日付・JSONの形・留学生の条件の項目を含む")
    void buildPrompt_containsRequiredParts() {
        String prompt = service.buildPrompt("① 12/7(月) オムロン様", TODAY);

        assertThat(prompt).contains("① 12/7(月) オムロン様", "2026-11-16", "JSON", "\"companies\"",
                "japanese", "foreignStudentNote", "推測せず", "1社も省略せず", "totalCount");
    }

    // ---------- parse ----------

    @Test
    @DisplayName("parse: 正しいJSON → 企業ごとに読み取る")
    void parse_valid() {
        CompanyImportService.ParseResult parsed = service.parse(TWO_COMPANIES, TODAY);
        List<CompanyImportService.CompanyCandidate> result = parsed.companies();

        assertThat(parsed.totalCount()).isEqualTo(2);
        assertThat(result).hasSize(2);
        assertThat(result.get(0).name()).isEqualTo("DMM.com");
        assertThat(result.get(0).positions()).containsExactly("Webエンジニア", "インフラ");
        assertThat(result.get(0).eventDate()).isEqualTo(LocalDate.of(2026, 12, 9));
        assertThat(result.get(1).foreignStudentNote()).isEqualTo("AWSの学習経験が必要");
    }

    @Test
    @DisplayName("parse: 名前が空は捨てる。過去・形式違いの日付は null、時刻が HH:mm でなければ空")
    void parse_cleans() {
        String json = """
                {"companies":[
                  {"name":"  "},
                  {"name":"A社","eventDate":"2026-11-01","eventTime":"午前"},
                  {"name":"B社","eventDate":"12月上旬","eventTime":"9:30"}
                ]}""";

        List<CompanyImportService.CompanyCandidate> result = service.parse(json, TODAY).companies();

        assertThat(result).extracting(CompanyImportService.CompanyCandidate::name).containsExactly("A社", "B社");
        assertThat(result.get(0).eventDate()).isNull();   // 過去
        assertThat(result.get(0).eventTime()).isEmpty();  // 「午前」は時刻ではない
        assertThat(result.get(1).eventDate()).isNull();   // 形式違い
        assertThat(result.get(1).eventTime()).isEqualTo("9:30");
        assertThat(result.get(1).positions()).isEmpty();  // null → 空のリスト
    }

    @Test
    @DisplayName("parse: 長い名前は100文字・業界は50文字で切る。最大20社")
    void parse_limits() {
        StringBuilder sb = new StringBuilder("{\"companies\":[");
        sb.append("{\"name\":\"").append("あ".repeat(150)).append("\",\"industry\":\"").append("い".repeat(80)).append("\"}");
        for (int i = 1; i <= 25; i++) {
            sb.append(",{\"name\":\"C").append(i).append("\"}");
        }
        sb.append("]}");

        List<CompanyImportService.CompanyCandidate> result = service.parse(sb.toString(), TODAY).companies();

        assertThat(result).hasSize(CompanyImportService.MAX_COMPANIES);
        assertThat(result.get(0).name()).hasSize(100);
        assertThat(result.get(0).industry()).hasSize(50);
    }

    @Test
    @DisplayName("parse: JSONとして読めない → AiException")
    void parse_brokenJson() {
        assertThatThrownBy(() -> service.parse("DMM.com：東京", TODAY)).isInstanceOf(AiException.class);
    }

    // ---------- extract / toFormItems ----------

    @Test
    @DisplayName("extract: 確認画面用にメモ・求人票・タスク名を組み立て、登録済みの企業に印をつける")
    void extract_buildsFormItems() {
        User user = new User();
        when(aiClient.generateJson(any())).thenReturn(TWO_COMPANIES);
        when(companyRepository.existsByUserIdAndName(user.getId(), "DMM.com")).thenReturn(true);
        when(companyRepository.existsByUserIdAndName(user.getId(), "KDDIアイレット")).thenReturn(false);

        CompanyImportService.ImportPreview preview = service.extract(user, "資料", TODAY);
        List<CompanyImportForm.Item> items = preview.items();

        assertThat(preview.warning()).isNull(); // totalCount と同じ数なので注意なし

        CompanyImportForm.Item dmm = items.get(0);
        assertThat(dmm.getPosition()).isEqualTo("Webエンジニア、インフラ");
        assertThat(dmm.getTaskTitle()).isEqualTo("企業説明会（11:00・オンライン）");
        assertThat(dmm.getMemo()).contains("【日本語】N2以上", "【勤務地】東京", "・大規模サービス");
        assertThat(dmm.getJdText()).contains("募集職種：Webエンジニア、インフラ", "説明会：2026-12-09 11:00 オンライン");
        assertThat(dmm.isExisting()).isTrue();
        assertThat(dmm.isSelected()).isFalse(); // 登録済みは最初チェックなし
        assertThat(items.get(1).isSelected()).isTrue();
        assertThat(items.get(1).getMemo()).contains("【留学生の条件】AWSの学習経験が必要");
        assertThat(items.get(1).isExisting()).isFalse();
    }

    @Test
    @DisplayName("extract: 資料が空・長すぎる → AiException（AIは呼ばない）")
    void extract_invalidText() {
        User user = new User();

        assertThatThrownBy(() -> service.extract(user, "  ", TODAY)).isInstanceOf(AiException.class);
        assertThatThrownBy(() -> service.extract(user, "あ".repeat(CompanyImportService.MAX_TEXT_LENGTH + 1), TODAY))
                .isInstanceOf(AiException.class);
        verify(aiClient, never()).generateJson(any());
    }

    // ---------- save ----------

    private static CompanyImportForm.Item item(String name, LocalDate eventDate, boolean selected) {
        CompanyImportForm.Item item = new CompanyImportForm.Item();
        item.setSelected(selected);
        item.setName(name);
        item.setIndustry("IT");
        item.setPosition("Webエンジニア");
        item.setEventDate(eventDate);
        item.setTaskTitle("企業説明会（11:00・オンライン）");
        item.setMemo("【日本語】N2以上");
        item.setJdText("募集職種：Webエンジニア");
        return item;
    }

    @Test
    @DisplayName("save: 新しい企業は作り、登録済みの企業は使い回す。応募は未応募、日付があればタスクを作る。未チェックは無視")
    void save_createsCompanyApplicationTask() {
        User user = new User();
        Company existing = new Company();
        when(companyRepository.findByUserIdAndName(user.getId(), "新しい会社")).thenReturn(Optional.empty());
        when(companyRepository.findByUserIdAndName(user.getId(), "登録済みの会社")).thenReturn(Optional.of(existing));

        CompanyImportForm form = new CompanyImportForm();
        form.setItems(List.of(
                item("新しい会社", LocalDate.of(2026, 12, 9), true),
                item("登録済みの会社", null, true),
                item("チェックなし", LocalDate.of(2026, 12, 9), false)));

        CompanyImportService.ImportResult result = service.save(user, form);

        assertThat(result).isEqualTo(new CompanyImportService.ImportResult(1, 1, 2, 0, 1));

        ArgumentCaptor<Company> company = ArgumentCaptor.forClass(Company.class);
        verify(companyRepository).save(company.capture());
        assertThat(company.getValue().getName()).isEqualTo("新しい会社");
        assertThat(company.getValue().getMemo()).isEqualTo("【日本語】N2以上");

        ArgumentCaptor<Application> apps = ArgumentCaptor.forClass(Application.class);
        verify(applicationRepository, times(2)).save(apps.capture());
        assertThat(apps.getAllValues()).allMatch(a -> a.getStatus() == ApplicationStatus.NOT_APPLIED);
        assertThat(apps.getAllValues().get(1).getCompany()).isSameAs(existing);

        ArgumentCaptor<Task> task = ArgumentCaptor.forClass(Task.class);
        verify(taskRepository).save(task.capture());
        assertThat(task.getValue().getDueDate()).isEqualTo(LocalDate.of(2026, 12, 9));
        assertThat(task.getValue().getTitle()).isEqualTo("企業説明会（11:00・オンライン）");
        verify(companyRepository, never()).findByUserIdAndName(any(), eq("チェックなし"));
    }

    // ---------- 省略の検出・職種の長さ ----------

    @Test
    @DisplayName("extract: AIが数えた企業数より少ない → 注意メッセージ")
    void extract_warnsWhenCompaniesMissing() {
        User user = new User();
        when(aiClient.generateJson(any())).thenReturn("{\"totalCount\":9,\"companies\":[{\"name\":\"A社\"},{\"name\":\"B社\"}]}");

        CompanyImportService.ImportPreview preview = service.extract(user, "資料", TODAY);

        assertThat(preview.items()).hasSize(2);
        assertThat(preview.warning()).contains("9 社", "2 社");
    }

    @Test
    @DisplayName("joinPositions: 100文字に入らない職種は途中で切らずに「ほか」にする")
    void joinPositions() {
        assertThat(CompanyImportService.joinPositions(List.of())).isEqualTo("（職種未定）");
        assertThat(CompanyImportService.joinPositions(List.of("Webエンジニア", "インフラ"))).isEqualTo("Webエンジニア、インフラ");

        String longName = "あ".repeat(60);
        String joined = CompanyImportService.joinPositions(List.of(longName, longName));
        assertThat(joined).isEqualTo(longName + "ほか");
        assertThat(CompanyImportService.joinPositions(List.of("い".repeat(150)))).hasSize(99);
    }

    @Test
    @DisplayName("parse: 日本語の条件が空で、留学生の条件に N1 などがあれば日本語の条件に移す")
    void parse_movesJlptToJapanese() {
        String json = """
                {"companies":[
                  {"name":"A社","japanese":"","foreignStudentNote":"N1以上"},
                  {"name":"B社","japanese":"","foreignStudentNote":"N2以上。4年制コースのみ"},
                  {"name":"C社","japanese":"N2以上","foreignStudentNote":"AWSの学習経験が必要"}
                ]}""";

        List<CompanyImportService.CompanyCandidate> result = service.parse(json, TODAY).companies();

        assertThat(result.get(0).japanese()).isEqualTo("N1以上");
        assertThat(result.get(0).foreignStudentNote()).isEmpty();
        assertThat(result.get(1).japanese()).isEqualTo("N2以上");
        assertThat(result.get(1).foreignStudentNote()).isEqualTo("N2以上。4年制コースのみ"); // 他の条件もあるので残す
        assertThat(result.get(2).foreignStudentNote()).isEqualTo("AWSの学習経験が必要");
    }

    @Test
    @DisplayName("save: 同じ資料を2回取り込んでも、既存の企業・応募・タスクは二重に作らない")
    void save_doesNotDuplicate() throws Exception {
        User user = new User();
        Company existing = new Company();
        setId(existing, 10L);
        Application existingApp = new Application();
        setId(existingApp, 20L);
        when(companyRepository.findByUserIdAndName(user.getId(), "DMM.com")).thenReturn(Optional.of(existing));
        when(applicationRepository.findFirstByCompanyIdAndPosition(10L, "Webエンジニア"))
                .thenReturn(Optional.of(existingApp));
        when(taskRepository.existsByApplicationIdAndTitleAndDueDate(20L, "企業説明会（11:00・オンライン）",
                LocalDate.of(2026, 12, 9))).thenReturn(true);

        CompanyImportForm form = new CompanyImportForm();
        form.setItems(List.of(item("DMM.com", LocalDate.of(2026, 12, 9), true)));

        CompanyImportService.ImportResult result = service.save(user, form);

        assertThat(result).isEqualTo(new CompanyImportService.ImportResult(0, 1, 0, 1, 0));
        verify(companyRepository, never()).save(any());
        verify(applicationRepository, never()).save(any());
        verify(taskRepository, never()).save(any());
    }

    // テスト用：DBが振る id をリフレクションで入れる（エンティティに setId がないため）
    private static void setId(Object entity, Long id) throws Exception {
        java.lang.reflect.Field field = entity.getClass().getDeclaredField("id");
        field.setAccessible(true);
        field.set(entity, id);
    }

    @Test
    @DisplayName("extract: 登録済みの企業は、既存の応募の職種を初期値にする（AIの言い方の違いで二重にならないように）")
    void extract_usesExistingPosition() {
        User user = new User();
        Application app = new Application();
        app.setPosition("Webエンジニア、インフラエンジニア");
        when(aiClient.generateJson(any())).thenReturn(
                "{\"totalCount\":1,\"companies\":[{\"name\":\"DMM.com\",\"positions\":[\"Webエンジニア\",\"インフラ領域\"]}]}");
        when(companyRepository.existsByUserIdAndName(user.getId(), "DMM.com")).thenReturn(true);
        when(applicationRepository.findByCompanyUserIdAndCompanyNameOrderByIdAsc(user.getId(), "DMM.com"))
                .thenReturn(List.of(app));

        CompanyImportForm.Item item = service.extract(user, "資料", TODAY).items().get(0);

        assertThat(item.getPosition()).isEqualTo("Webエンジニア、インフラエンジニア");
        assertThat(item.getExistingPositions()).containsExactly("Webエンジニア、インフラエンジニア");
        assertThat(item.isSelected()).isFalse();
    }
}

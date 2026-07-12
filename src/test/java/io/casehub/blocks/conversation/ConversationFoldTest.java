package io.casehub.blocks.conversation;

import org.junit.jupiter.api.Nested;
import io.casehub.api.model.TaskStatus;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ConversationFoldTest {

    private final ConversationState empty = new ConversationState(
            Map.of(), List.of(), List.of(), Map.of());

    // ── createPoint ─────────────────────────────────────────────────────────

    @Nested
    class CreatePoint {

        @Test
        void createsPointWithOpenStatus() {
            var classification = new PointClassification(Priority.HIGH, "global", "section 3.2");
            var result = ConversationFold.createPoint(empty,
                    "point-1", classification, "REVIEWER", 1, "RAISE", "This needs attention");

            assertThat(result.points()).containsKey("point-1");
            ConversationPoint point = result.points().get("point-1");
            assertThat(point.id()).isEqualTo("point-1");
            assertThat(point.status()).isEqualTo(ConversationProtocol.STATUS_OPEN);
            assertThat(point.classification()).isEqualTo(classification);
            assertThat(point.thread()).hasSize(1);

            ThreadEntry entry = point.thread().get(0);
            assertThat(entry.entryId()).isEqualTo("point-1");
            assertThat(entry.role()).isEqualTo("REVIEWER");
            assertThat(entry.round()).isEqualTo(1);
            assertThat(entry.entryType()).isEqualTo("RAISE");
            assertThat(entry.content()).isEqualTo("This needs attention");
        }

        @Test
        void preservesExistingPoints() {
            var classification = new PointClassification(Priority.MEDIUM, null, null);
            var state = ConversationFold.createPoint(empty,
                    "point-1", classification, "REV", 1, "RAISE", "first");
            var result = ConversationFold.createPoint(state,
                    "point-2", classification, "REV", 1, "RAISE", "second");

            assertThat(result.points()).hasSize(2);
            assertThat(result.points()).containsKeys("point-1", "point-2");
        }

        @Test
        void preservesExistingMemosAndFlags() {
            var stateWithMemo = ConversationFold.addMemo(empty, "REV", 1, "a memo");
            var stateWithFlag = ConversationFold.flagHuman(stateWithMemo, null, "REV", 1, "help");

            var classification = new PointClassification(Priority.LOW, null, null);
            var result = ConversationFold.createPoint(stateWithFlag,
                    "point-1", classification, "REV", 1, "RAISE", "body");

            assertThat(result.memos()).hasSize(1);
            assertThat(result.humanFlags()).hasSize(1);
        }

        @Test
        void nullPriorityUsedAsIs() {
            var classification = new PointClassification(null, null, null);
            var result = ConversationFold.createPoint(empty,
                    "p1", classification, "REV", 0, "RAISE", "body");

            assertThat(result.points().get("p1").classification().priority()).isNull();
        }
    }

    // ── respondToPoint ──────────────────────────────────────────────────────

    @Nested
    class RespondToPoint {

        @Test
        void appendsToThreadAndUpdatesStatus() {
            var classification = new PointClassification(Priority.MEDIUM, null, null);
            var state = ConversationFold.createPoint(empty,
                    "point-1", classification, "REVIEWER", 1, "RAISE", "the point");

            var result = ConversationFold.respondToPoint(state,
                    "point-1", "IMPLEMENTOR", 1, "AGREE", "I agree", "AGREED");

            ConversationPoint point = result.points().get("point-1");
            assertThat(point.status()).isEqualTo("AGREED");
            assertThat(point.thread()).hasSize(2);

            ThreadEntry response = point.thread().get(1);
            assertThat(response.entryId()).isNull();
            assertThat(response.role()).isEqualTo("IMPLEMENTOR");
            assertThat(response.round()).isEqualTo(1);
            assertThat(response.entryType()).isEqualTo("AGREE");
            assertThat(response.content()).isEqualTo("I agree");
        }

        @Test
        void nullNewStatusPreservesExistingStatus() {
            var classification = new PointClassification(Priority.LOW, null, null);
            var state = ConversationFold.createPoint(empty,
                    "point-1", classification, "REV", 1, "RAISE", "body");

            var result = ConversationFold.respondToPoint(state,
                    "point-1", "IMP", 1, "UNKNOWN_TYPE", "content", null);

            assertThat(result.points().get("point-1").status())
                    .isEqualTo(ConversationProtocol.STATUS_OPEN);
            assertThat(result.points().get("point-1").thread()).hasSize(2);
        }

        @Test
        void nonExistentTargetReturnsStateUnchanged() {
            var result = ConversationFold.respondToPoint(empty,
                    "no-such-point", "IMP", 1, "AGREE", "content", "AGREED");

            assertThat(result).isSameAs(empty);
        }

        @Test
        void preservesOtherPoints() {
            var classification = new PointClassification(Priority.LOW, null, null);
            var state = ConversationFold.createPoint(empty,
                    "p1", classification, "REV", 1, "RAISE", "first");
            state = ConversationFold.createPoint(state,
                    "p2", classification, "REV", 1, "RAISE", "second");

            var result = ConversationFold.respondToPoint(state,
                    "p1", "IMP", 1, "AGREE", "ok", "AGREED");

            assertThat(result.points().get("p1").status()).isEqualTo("AGREED");
            assertThat(result.points().get("p2").status()).isEqualTo(ConversationProtocol.STATUS_OPEN);
        }
    }

    // ── flagHuman ───────────────────────────────────────────────────────────

    @Nested
    class FlagHuman {

        @Test
        void withTargetPointEscalatesAndAddsFlag() {
            var classification = new PointClassification(Priority.HIGH, null, null);
            var state = ConversationFold.createPoint(empty,
                    "point-1", classification, "REV", 1, "RAISE", "a point");

            var result = ConversationFold.flagHuman(state,
                    "point-1", "REV", 2, "human help needed");

            ConversationPoint point = result.points().get("point-1");
            assertThat(point.thread()).hasSize(2);
            assertThat(point.thread().get(1).entryType()).isEqualTo(ConversationProtocol.FLAG_HUMAN);
            assertThat(point.thread().get(1).content()).isEqualTo("human help needed");
            assertThat(point.status()).isEqualTo(ConversationProtocol.STATUS_ESCALATED);

            assertThat(result.humanFlags()).hasSize(1);
            FlagEntry flag = result.humanFlags().get(0);
            assertThat(flag.role()).isEqualTo("REV");
            assertThat(flag.round()).isEqualTo(2);
            assertThat(flag.content()).isEqualTo("human help needed");
        }

        @Test
        void withNonExistentTargetAddsFlagOnly() {
            var result = ConversationFold.flagHuman(empty,
                    "no-such-point", "REV", 1, "general escalation");

            assertThat(result.points()).isEmpty();
            assertThat(result.humanFlags()).hasSize(1);
            assertThat(result.humanFlags().get(0).content()).isEqualTo("general escalation");
        }

        @Test
        void withNullTargetAddsFlagOnly() {
            var result = ConversationFold.flagHuman(empty,
                    null, "REV", 1, "escalation without target");

            assertThat(result.points()).isEmpty();
            assertThat(result.humanFlags()).hasSize(1);
        }

        @Test
        void accumulatesMultipleFlags() {
            var state = ConversationFold.flagHuman(empty, null, "REV", 1, "first");
            var result = ConversationFold.flagHuman(state, null, "IMP", 2, "second");

            assertThat(result.humanFlags()).hasSize(2);
        }
    }

    // ── addMemo ─────────────────────────────────────────────────────────────

    @Nested
    class AddMemo {

        @Test
        void appendsRoundMemo() {
            var result = ConversationFold.addMemo(empty, "REVIEWER", 2, "Round 2 observations");

            assertThat(result.memos()).hasSize(1);
            RoundMemo memo = result.memos().get(0);
            assertThat(memo.role()).isEqualTo("REVIEWER");
            assertThat(memo.round()).isEqualTo(2);
            assertThat(memo.content()).isEqualTo("Round 2 observations");
        }

        @Test
        void accumulatesMultipleMemos() {
            var state = ConversationFold.addMemo(empty, "REV", 1, "first");
            var result = ConversationFold.addMemo(state, "IMP", 2, "second");

            assertThat(result.memos()).hasSize(2);
        }

        @Test
        void preservesExistingPoints() {
            var classification = new PointClassification(Priority.LOW, null, null);
            var state = ConversationFold.createPoint(empty,
                    "p1", classification, "REV", 1, "RAISE", "body");

            var result = ConversationFold.addMemo(state, "REV", 1, "memo");

            assertThat(result.points()).hasSize(1);
            assertThat(result.memos()).hasSize(1);
        }
    }

    // ── requestSubTask ──────────────────────────────────────────────────────

    @Nested
    class RequestSubTask {

        @Test
        void createsPendingSubTask() {
            var result = ConversationFold.requestSubTask(empty,
                    "st-1", "VERIFY", "REVIEWER", "point-1");

            assertThat(result.subTaskFindings()).containsKey("st-1");
            SubTaskFinding f = result.subTaskFindings().get("st-1");
            assertThat(f.subTaskId()).isEqualTo("st-1");
            assertThat(f.taskType()).isEqualTo("VERIFY");
            assertThat(f.requestedBy()).isEqualTo("REVIEWER");
            assertThat(f.pointId()).isEqualTo("point-1");
            assertThat(f.finding()).isNull();
            assertThat(f.errorReason()).isNull();
            assertThat(f.status()).isEqualTo(TaskStatus.PENDING);
        }

        @Test
        void nullPointIdAllowed() {
            var result = ConversationFold.requestSubTask(empty,
                    "st-1", "CUSTOM", "REV", null);

            assertThat(result.subTaskFindings().get("st-1").pointId()).isNull();
        }
    }

    // ── completeSubTask ─────────────────────────────────────────────────────

    @Nested
    class CompleteSubTask {

        @Test
        void withPriorRequestPreservesRequestedBy() {
            var state = ConversationFold.requestSubTask(empty,
                    "st-1", "VERIFY", "REVIEWER", "point-1");

            var result = ConversationFold.completeSubTask(state,
                    "st-1", "VERIFY", "ARBITRATOR", "point-1", "Claim verified");

            SubTaskFinding f = result.subTaskFindings().get("st-1");
            assertThat(f.status()).isEqualTo(TaskStatus.COMPLETED);
            assertThat(f.finding()).isEqualTo("Claim verified");
            assertThat(f.requestedBy()).isEqualTo("REVIEWER");
            assertThat(f.pointId()).isEqualTo("point-1");
        }

        @Test
        void withoutPriorRequestUsesProvidedRole() {
            var result = ConversationFold.completeSubTask(empty,
                    "st-new", "VERIFY", "ARBITRATOR", "point-2", "finding content");

            SubTaskFinding f = result.subTaskFindings().get("st-new");
            assertThat(f.status()).isEqualTo(TaskStatus.COMPLETED);
            assertThat(f.requestedBy()).isEqualTo("ARBITRATOR");
        }

        @Test
        void preservesPointIdFromOriginalRequest() {
            var state = ConversationFold.requestSubTask(empty,
                    "st-1", "VERIFY", "REV", "original-point");

            var result = ConversationFold.completeSubTask(state,
                    "st-1", "VERIFY", "ARB", null, "done");

            assertThat(result.subTaskFindings().get("st-1").pointId()).isEqualTo("original-point");
        }
    }

    // ── errorSubTask ────────────────────────────────────────────────────────

    @Nested
    class ErrorSubTask {

        @Test
        void withPriorRequestPreservesRequestedBy() {
            var state = ConversationFold.requestSubTask(empty,
                    "st-2", "CUSTOM", "IMPLEMENTOR", "point-3");

            var result = ConversationFold.errorSubTask(state,
                    "st-2", "CUSTOM", "ARBITRATOR", "task failed");

            SubTaskFinding f = result.subTaskFindings().get("st-2");
            assertThat(f.status()).isEqualTo(TaskStatus.FAULTED);
            assertThat(f.errorReason()).isEqualTo("task failed");
            assertThat(f.requestedBy()).isEqualTo("IMPLEMENTOR");
            assertThat(f.pointId()).isEqualTo("point-3");
        }

        @Test
        void withoutPriorRequestUsesProvidedRole() {
            var result = ConversationFold.errorSubTask(empty,
                    "st-orphan", "CUSTOM", "ARBITRATOR", "error reason");

            SubTaskFinding f = result.subTaskFindings().get("st-orphan");
            assertThat(f.status()).isEqualTo(TaskStatus.FAULTED);
            assertThat(f.requestedBy()).isEqualTo("ARBITRATOR");
        }

        @Test
        void preservesPointIdFromOriginalRequest() {
            var state = ConversationFold.requestSubTask(empty,
                    "st-1", "VERIFY", "REV", "original-point");

            var result = ConversationFold.errorSubTask(state,
                    "st-1", "VERIFY", "ARB", "failed");

            assertThat(result.subTaskFindings().get("st-1").pointId()).isEqualTo("original-point");
        }
    }
}

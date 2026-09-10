package com.community.notification_service.services;

import com.community.notification_service.dtos.request.SystemNotificationRequest;
import com.community.notification_service.dtos.response.NotificationResponse;
import com.community.notification_service.entity.Notification;
import com.community.notification_service.entity.Enum.NotificationType;
import com.community.notification_service.repository.NotificationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("NotificationService")
class NotificationServiceTest {

    @Mock private NotificationRepository notificationRepository;

    private NotificationService notificationService;

    @BeforeEach
    void setUp() {
        notificationService = new NotificationService(notificationRepository);
    }

    private SystemNotificationRequest buildRequest(String userId, NotificationType type) {
        SystemNotificationRequest req = new SystemNotificationRequest();
        req.setUserId(userId);
        req.setType(type);
        req.setTitle("Test Title");
        req.setMessage("Test Message");
        req.setActionUrl("/projects/abc");
        req.setActorId("actor-99");
        req.setActorName("ActorUser");
        return req;
    }

    private Notification buildNotification(String id, String userId, boolean read) {
        return Notification.builder()
                .id(id)
                .userId(userId)
                .type(NotificationType.GENERAL)
                .title("Title")
                .message("Message")
                .read(read)
                .createdAt(Instant.now())
                .build();
    }

    private NotificationResponse toResponse(Notification n) {
        return NotificationResponse.builder()
                .id(n.getId())
                .type(n.getType())
                .title(n.getTitle())
                .message(n.getMessage())
                .read(n.isRead())
                .createdAt(n.getCreatedAt())
                .build();
    }

    @Nested
    @DisplayName("create()")
    class CreateTests {

        @Test
        @DisplayName("saves notification with all fields from request")
        void savesNotificationWithAllFields() {
            SystemNotificationRequest req = buildRequest("user-1", NotificationType.NEW_FOLLOWER);
            Notification saved = buildNotification("notif-1", "user-1", false);
            when(notificationRepository.save(any())).thenReturn(saved);

            Notification result = notificationService.create(req);

            assertThat(result).isNotNull();
            assertThat(result.getId()).isEqualTo("notif-1");

            ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
            verify(notificationRepository).save(captor.capture());
            Notification captured = captor.getValue();
            assertThat(captured.getUserId()).isEqualTo("user-1");
            assertThat(captured.getType()).isEqualTo(NotificationType.NEW_FOLLOWER);
            assertThat(captured.getTitle()).isEqualTo("Test Title");
            assertThat(captured.getMessage()).isEqualTo("Test Message");
            assertThat(captured.getActionUrl()).isEqualTo("/projects/abc");
            assertThat(captured.getActorId()).isEqualTo("actor-99");
            assertThat(captured.getActorName()).isEqualTo("ActorUser");
        }

        @Test
        @DisplayName("new notification is always created with read=false")
        void newNotificationIsUnread() {
            SystemNotificationRequest req = buildRequest("user-1", NotificationType.GENERAL);
            when(notificationRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            notificationService.create(req);

            ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
            verify(notificationRepository).save(captor.capture());
            assertThat(captor.getValue().isRead()).isFalse();
        }

        @Test
        @DisplayName("createdAt is set on the new notification")
        void createdAtIsSet() {
            SystemNotificationRequest req = buildRequest("user-1", NotificationType.GENERAL);
            when(notificationRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            Instant before = Instant.now().minusSeconds(1);
            notificationService.create(req);
            Instant after = Instant.now().plusSeconds(1);

            ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
            verify(notificationRepository).save(captor.capture());
            assertThat(captor.getValue().getCreatedAt()).isBetween(before, after);
        }
    }

    @Nested
    @DisplayName("getAll()")
    class GetAllTests {

        @Test
        @DisplayName("returns all notifications for the given user, mapped to response DTOs")
        void returnsAllNotificationsForUser() {
            Notification n1 = buildNotification("n1", "user-1", false);
            Notification n2 = buildNotification("n2", "user-1", true);
            when(notificationRepository.findAllByUserIdOrderByCreatedAtDesc("user-1"))
                    .thenReturn(List.of(n1, n2));

            List<NotificationResponse> result = notificationService.getAll("user-1");

            assertThat(result).hasSize(2);
            assertThat(result.get(0).getId()).isEqualTo("n1");
            assertThat(result.get(1).getId()).isEqualTo("n2");
        }

        @Test
        @DisplayName("returns empty list when user has no notifications")
        void returnsEmptyListWhenNoNotifications() {
            when(notificationRepository.findAllByUserIdOrderByCreatedAtDesc("user-1"))
                    .thenReturn(List.of());

            assertThat(notificationService.getAll("user-1")).isEmpty();
        }
    }

    @Nested
    @DisplayName("getUnread()")
    class GetUnreadTests {

        @Test
        @DisplayName("returns only unread notifications")
        void returnsOnlyUnread() {
            Notification unread = buildNotification("n1", "user-1", false);
            when(notificationRepository.findAllByUserIdAndReadFalseOrderByCreatedAtDesc("user-1"))
                    .thenReturn(List.of(unread));

            List<NotificationResponse> result = notificationService.getUnread("user-1");

            assertThat(result).hasSize(1);
            assertThat(result.get(0).isRead()).isFalse();
        }

        @Test
        @DisplayName("returns empty list when all are read")
        void returnsEmptyWhenAllRead() {
            when(notificationRepository.findAllByUserIdAndReadFalseOrderByCreatedAtDesc("user-1"))
                    .thenReturn(List.of());

            assertThat(notificationService.getUnread("user-1")).isEmpty();
        }
    }

    @Nested
    @DisplayName("getUnreadCount()")
    class GetUnreadCountTests {

        @Test
        @DisplayName("delegates to repository and returns correct count")
        void returnsUnreadCount() {
            when(notificationRepository.countByUserIdAndReadFalse("user-1")).thenReturn(5L);

            assertThat(notificationService.getUnreadCount("user-1")).isEqualTo(5L);
        }

        @Test
        @DisplayName("returns 0 when user has no unread notifications")
        void returnsZeroWhenNoneUnread() {
            when(notificationRepository.countByUserIdAndReadFalse("user-1")).thenReturn(0L);

            assertThat(notificationService.getUnreadCount("user-1")).isZero();
        }
    }

    @Nested
    @DisplayName("markRead()")
    class MarkReadTests {

        @Test
        @DisplayName("marks notification as read when owned by the requesting user")
        void marksReadWhenOwner() {
            Notification n = buildNotification("n1", "user-1", false);
            when(notificationRepository.findById("n1")).thenReturn(Optional.of(n));

            notificationService.markRead("n1", "user-1");

            assertThat(n.isRead()).isTrue();
            assertThat(n.getReadAt()).isNotNull();
            verify(notificationRepository).save(n);
        }

        @Test
        @DisplayName("does NOT mark read when notification belongs to a different user")
        void doesNotMarkReadForWrongUser() {
            Notification n = buildNotification("n1", "user-1", false);
            when(notificationRepository.findById("n1")).thenReturn(Optional.of(n));

            notificationService.markRead("n1", "user-INTRUDER");

            assertThat(n.isRead()).isFalse();
            verify(notificationRepository, never()).save(any());
        }

        @Test
        @DisplayName("does nothing when notification ID does not exist")
        void doesNothingWhenNotFound() {
            when(notificationRepository.findById("missing")).thenReturn(Optional.empty());

            notificationService.markRead("missing", "user-1");

            verify(notificationRepository, never()).save(any());
        }
    }

    @Nested
    @DisplayName("markAllRead()")
    class MarkAllReadTests {

        @Test
        @DisplayName("marks all unread notifications as read for the given user")
        void marksAllUnread() {
            Notification n1 = buildNotification("n1", "user-1", false);
            Notification n2 = buildNotification("n2", "user-1", false);
            when(notificationRepository.findAllByUserIdAndReadFalse("user-1"))
                    .thenReturn(List.of(n1, n2));

            notificationService.markAllRead("user-1");

            assertThat(n1.isRead()).isTrue();
            assertThat(n2.isRead()).isTrue();
            assertThat(n1.getReadAt()).isNotNull();
            assertThat(n2.getReadAt()).isNotNull();
            verify(notificationRepository).saveAll(List.of(n1, n2));
        }

        @Test
        @DisplayName("does nothing when all notifications are already read")
        void doesNothingWhenAllAlreadyRead() {
            when(notificationRepository.findAllByUserIdAndReadFalse("user-1"))
                    .thenReturn(List.of());

            notificationService.markAllRead("user-1");

            verify(notificationRepository).saveAll(List.of());
        }
    }

    @Nested
    @DisplayName("delete()")
    class DeleteTests {

        @Test
        @DisplayName("deletes notification when owned by the requesting user")
        void deletesWhenOwner() {
            Notification n = buildNotification("n1", "user-1", false);
            when(notificationRepository.findById("n1")).thenReturn(Optional.of(n));

            notificationService.delete("n1", "user-1");

            verify(notificationRepository).delete(n);
        }

        @Test
        @DisplayName("does NOT delete when notification belongs to a different user")
        void doesNotDeleteForWrongUser() {
            Notification n = buildNotification("n1", "user-1", false);
            when(notificationRepository.findById("n1")).thenReturn(Optional.of(n));

            notificationService.delete("n1", "user-INTRUDER");

            verify(notificationRepository, never()).delete(any());
        }

        @Test
        @DisplayName("does nothing when notification ID does not exist")
        void doesNothingWhenNotFound() {
            when(notificationRepository.findById("missing")).thenReturn(Optional.empty());

            notificationService.delete("missing", "user-1");

            verify(notificationRepository, never()).delete(any());
        }
    }
}

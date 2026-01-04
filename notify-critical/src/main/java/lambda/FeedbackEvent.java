package lambda;

public record FeedbackEvent(
        String feedbackId,
        String fullName,
        String category,
        String comment,
        int rating,
        boolean isCritical
) {}

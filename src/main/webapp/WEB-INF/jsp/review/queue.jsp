<!DOCTYPE html>
<html lang="en">
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<%@ taglib prefix="fmt" uri="http://java.sun.com/jsp/jstl/fmt" %>
<head>
    <meta charset="UTF-8">
    <title>Manual Review Queue - IRE</title>
    <link rel="stylesheet" href="https://cdn.jsdelivr.net/npm/bootstrap@4.6.2/dist/css/bootstrap.min.css">
</head>
<body>
<nav class="navbar navbar-dark bg-dark">
    <a class="navbar-brand" href="${pageContext.request.contextPath}/">IRE Dashboard</a>
    <span class="navbar-text text-white">Manual Review Queue</span>
</nav>

<div class="container-fluid mt-4">
    <h3>Pending Reviews</h3>
    <hr>

    <c:choose>
        <c:when test="${empty reviews}">
            <div class="alert alert-success">No pending reviews - queue is clear!</div>
        </c:when>
        <c:otherwise>
            <table class="table table-striped table-bordered">
                <thead class="thead-dark">
                    <tr>
                        <th>Review ID</th>
                        <th>Source System</th>
                        <th>Confidence Score</th>
                        <th>Status</th>
                        <th>Created Date</th>
                        <th>Actions</th>
                    </tr>
                </thead>
                <tbody>
                    <c:forEach var="review" items="${reviews}">
                        <tr>
                            <td>${review.reviewId}</td>
                            <td>${review.sourceSystem}</td>
                            <td><fmt:formatNumber value="${review.confidenceScore}" pattern="0.000"/></td>
                            <td><span class="badge badge-warning">${review.status}</span></td>
                            <td>${review.createdDate}</td>
                            <td>
                                <button class="btn btn-success btn-sm"
                                        onclick="approveReview('${review.reviewId}')">Approve</button>
                                <button class="btn btn-danger btn-sm"
                                        onclick="rejectReview('${review.reviewId}')">Reject</button>
                            </td>
                        </tr>
                    </c:forEach>
                </tbody>
            </table>
        </c:otherwise>
    </c:choose>
</div>

<script src="https://code.jquery.com/jquery-3.5.1.min.js"></script>
<script>
function approveReview(reviewId) {
    $.ajax({
        url: '/api/v1/reviews/' + reviewId + '/approve',
        type: 'POST',
        contentType: 'application/json',
        data: JSON.stringify({ reviewer: 'ADMIN', notes: 'Approved via dashboard' }),
        success: function() { location.reload(); },
        error: function() { alert('Error approving review'); }
    });
}
function rejectReview(reviewId) {
    $.ajax({
        url: '/api/v1/reviews/' + reviewId + '/reject',
        type: 'POST',
        contentType: 'application/json',
        data: JSON.stringify({ reviewer: 'ADMIN', notes: 'Rejected via dashboard' }),
        success: function() { location.reload(); },
        error: function() { alert('Error rejecting review'); }
    });
}
</script>
</body>
</html>

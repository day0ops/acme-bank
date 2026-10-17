// Package restapi exposes the Accounts service over REST/JSON, calling the
// same grpcapi.Server methods in-process as the gRPC transport.
package restapi

import (
	"encoding/json"
	"net/http"

	"google.golang.org/grpc/codes"
	"google.golang.org/grpc/status"
)

// Problem is an RFC 9457 Problem Details object.
type Problem struct {
	Type   string `json:"type"`
	Title  string `json:"title"`
	Status int    `json:"status"`
	Detail string `json:"detail,omitempty"`
}

func writeProblem(w http.ResponseWriter, httpStatus int, title, detail string) {
	w.Header().Set("Content-Type", "application/problem+json")
	w.WriteHeader(httpStatus)
	_ = json.NewEncoder(w).Encode(Problem{
		Type:   "about:blank",
		Title:  title,
		Status: httpStatus,
		Detail: detail,
	})
}

// writeGRPCError maps a gRPC status error to the appropriate Problem Details response.
func writeGRPCError(w http.ResponseWriter, err error) {
	st, ok := status.FromError(err)
	if !ok {
		writeProblem(w, http.StatusInternalServerError, "Internal Server Error", err.Error())
		return
	}
	switch st.Code() {
	case codes.NotFound:
		writeProblem(w, http.StatusNotFound, "Not Found", st.Message())
	case codes.InvalidArgument:
		writeProblem(w, http.StatusBadRequest, "Bad Request", st.Message())
	case codes.FailedPrecondition:
		writeProblem(w, http.StatusUnprocessableEntity, "Unprocessable Entity", st.Message())
	default:
		writeProblem(w, http.StatusInternalServerError, "Internal Server Error", st.Message())
	}
}

package auth

import (
	"encoding/json"
	"net/http"
)

// problem is an RFC 9457 Problem Details object. Duplicated from the
// restapi package's Problem type rather than imported, since restapi
// already imports auth and importing it back here would create a cycle.
type problem struct {
	Type   string `json:"type"`
	Title  string `json:"title"`
	Status int    `json:"status"`
	Detail string `json:"detail,omitempty"`
}

func writeProblem(w http.ResponseWriter, status int, title, detail string) {
	w.Header().Set("Content-Type", "application/problem+json")
	w.WriteHeader(status)
	_ = json.NewEncoder(w).Encode(problem{
		Type:   "about:blank",
		Title:  title,
		Status: status,
		Detail: detail,
	})
}

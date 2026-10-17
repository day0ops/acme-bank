package commonv1

import (
	"testing"

	"google.golang.org/protobuf/proto"
)

func TestMoney_MarshalUnmarshalRoundTrip(t *testing.T) {
	original := &Money{CurrencyCode: "USD", MinorUnits: 12345}

	data, err := proto.Marshal(original)
	if err != nil {
		t.Fatalf("Marshal() error = %v", err)
	}

	got := &Money{}
	if err := proto.Unmarshal(data, got); err != nil {
		t.Fatalf("Unmarshal() error = %v", err)
	}

	if got.CurrencyCode != original.CurrencyCode || got.MinorUnits != original.MinorUnits {
		t.Fatalf("round trip mismatch: got %+v, want %+v", got, original)
	}
}

module example.com/acmebank/services/accounts

go 1.27.0

require example.com/acmebank/proto/gen/go v0.0.0

require google.golang.org/protobuf v1.36.12 // indirect

replace example.com/acmebank/proto/gen/go => ../../proto/gen/go

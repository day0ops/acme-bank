module example.com/acmebank/services/dashboard-bff

go 1.27

require (
	example.com/acmebank/proto/gen/go v0.0.0
	github.com/MicahParks/keyfunc/v3 v3.8.2
	github.com/golang-jwt/jwt/v5 v5.3.1
	google.golang.org/grpc v1.84.0
)

require (
	github.com/MicahParks/jwkset v0.11.3 // indirect
	golang.org/x/net v0.57.0 // indirect
	golang.org/x/sys v0.47.0 // indirect
	golang.org/x/text v0.40.0 // indirect
	golang.org/x/time v0.15.0 // indirect
	google.golang.org/genproto/googleapis/rpc v0.0.0-20260706201446-f0a921348800 // indirect
	google.golang.org/protobuf v1.36.12 // indirect
)

replace example.com/acmebank/proto/gen/go => ../../proto/gen/go

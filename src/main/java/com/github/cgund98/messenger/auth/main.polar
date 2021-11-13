allow(actor, action, resource) if
    has_permission(actor, action, resource);

actor User {}

resource Server {
    permissions = ["read", "update", "delete"];
    roles = ["member", "admin"];

    "read" if "admin";
    "update" if "admin";
    "delete" if "admin";

}

has_role(_: User, "reader", _: Server);
has_role(user: User, "admin", server: Server) if user.id = server.ownerId;
import handleUpdate from "./helpers/handleUpdate";

export default function(clusters, message) {
    return handleUpdate(
        clusters,
        message,
        { containerState: message.containerState },
    );
}

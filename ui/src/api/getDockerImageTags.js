import R from "ramda";

export default function getDockerImageTags(serverUrl) {
    return fetch(`${serverUrl}/dockerImages`)
        .then(response => response.json())
        .then((dockerImages) => {
            const tags = R.map(R.prop("tag"), dockerImages);
            const getImageNumber = R.compose(parseInt, R.join(""), R.takeLastWhile(x => x !== "-"));
            const sortByImageNumberDesc = R.sortBy(R.compose(R.negate, getImageNumber));
            const sortedTags = sortByImageNumberDesc(tags);
            return sortedTags;
        });
}

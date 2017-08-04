import R from "ramda";

export default function getDockerImages(serverUrl) {
    return fetch(`${serverUrl}/dockerImages`)
        .then(response => response.json())
        .then((dockerImages) => {
            const getImageNumber = R.compose(parseInt, R.join(""), R.takeLastWhile(x => x !== "-"), R.prop("tag"));
            const sortByImageNumberDesc = R.sortBy(R.compose(R.negate, getImageNumber));
            const sortedImages = sortByImageNumberDesc(dockerImages);
            return sortedImages;
        });
}

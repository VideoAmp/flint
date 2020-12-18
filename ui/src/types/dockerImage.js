import PropTypes from "prop-types";

export default PropTypes.shape({
    repo: PropTypes.string.isRequired,
    tag: PropTypes.string.isRequired,
});

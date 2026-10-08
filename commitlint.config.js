// Conventional Commits, with the repository's 120-character line limit instead of the default 100.
// Footers are not limited: trailers carry names and e-mail addresses.
module.exports = {
  extends: ['@commitlint/config-conventional'],
  rules: {
    'header-max-length': [2, 'always', 120],
    'body-max-line-length': [2, 'always', 120],
    'footer-max-line-length': [0],
  },
};

# Contributing to PEG

Thank you for your interest in contributing to the PEG Gateway project!

## Development Setup

### Prerequisites

- Java 17 or higher
- Maven 3.6+
- Azure AD tenant (for testing authentication)

### Getting Started

1. **Clone the repository**
   ```bash
   git clone https://github.com/numaansystems/peg.git
   cd peg
   ```

2. **Set up environment variables**
   ```bash
   cp .env.example .env
   # Edit .env with your Azure AD credentials
   ```

3. **Build the project**
   ```bash
   mvn clean package
   ```

4. **Run tests**
   ```bash
   mvn test
   ```

5. **Run the application**
   ```bash
   java -jar target/peg-1.0.0-SNAPSHOT.jar
   ```

## Code Style

- Follow standard Java conventions
- Use meaningful variable and method names
- Add JavaDoc comments for public methods
- Keep methods focused and concise

## Testing

- Write unit tests for new functionality
- Ensure all tests pass before submitting PR
- Maintain or improve code coverage

## Submitting Changes

1. Fork the repository
2. Create a feature branch (`git checkout -b feature/your-feature`)
3. Make your changes
4. Add tests for your changes
5. Commit with clear messages (`git commit -m 'Add feature X'`)
6. Push to your fork (`git push origin feature/your-feature`)
7. Open a Pull Request

## Pull Request Guidelines

- Describe the changes made
- Reference any related issues
- Ensure CI passes
- Update documentation if needed

## Reporting Issues

When reporting issues, please include:

- Description of the problem
- Steps to reproduce
- Expected vs actual behavior
- Environment details (Java version, OS, etc.)

## Questions?

Feel free to open an issue for any questions or discussions.
